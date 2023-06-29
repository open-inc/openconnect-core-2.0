package de.openinc.openconnect.plcconnector;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.openconnect.OpenConnect;
import de.openinc.openconnect.serivces.ServiceProvider;

public class PLCReaderRunnable implements Runnable {

	// Logging
	private static Logger logger = LoggerFactory.getLogger(OpenConnect.class);

	private List<JSONObject> items;

	private JSONObject options;
	private boolean connecting;
	private boolean mqttConnecting;
	private long mqttLastConnectTry;
	private boolean running;
	private MqttAsyncClient asyncClient;
	private String suffix;
	private String prefix;
	private boolean subscribed;
	private long lastTS = System.currentTimeMillis();
	private double count = 0;
	private ScheduledFuture<?> future;
	int backoff = 0;

	private PLCAdapter plcConnection = null;
	private HashMap<String, OpenWareDataItem> cache;

	public PLCReaderRunnable(JSONObject _options, String _prefix) throws Exception {
		options = _options;
		prefix = _prefix;
		this.subscribed = false;
		items = new ArrayList<JSONObject>();
		cache = new HashMap<String, OpenWareDataItem>();
		running = true;
		mqttConnecting = false;
		mqttLastConnectTry = 0l;
		logger.info(options.getString("connectionString") + ": Reader initialized");

		try {
			connectMQTT();
		} catch (Exception e) {
			logger.warn("Could not connect to MQTT Broker\n" + e.getMessage());
		}

		PLCAdapter adapterImp = ServiceProvider
				.getPlcAdapterFromConnectionString(options.getString("connectionString"));
		if (adapterImp == null) {
			throw new Error("Adapter could not be found.");
		}
		OpenConnect.getInstance().logger.info("Found adapter for connection string: " + adapterImp.getAdapterId());
		plcConnection = adapterImp;
		OpenConnect.getInstance().logger.info("PlcConnection loaded.");
	}

	public void cancel() throws Exception {
		plcConnection.disconnect();
		future.cancel(false);
	}
	public PLCAdapter getAdapter() {
		return plcConnection;
	}
	private boolean establishConnectionToPlc(int retryCount) throws Exception {
		logger.info(options.getString("connectionString") + ": connecting...");
		if (plcConnection != null) {
			if (plcConnection.isConnected()) {
				logger.info("Already connected to " + options.getString("connectionString"));
				backoff = 0;
				connecting = false;
				return true;
			}
			if (connecting) {
				logger.info("Connecting already started");
				Thread.sleep(2000);
				return false;
			}

			connecting = true;
			try {

				plcConnection.connect(options);
				logger.info(options.getString("connectionString") + ": Connection "
						+ (plcConnection.isConnected() ? "established" : "failed"));
				connecting = false;
				backoff = 0;
				return true;
			} catch (Exception e) {
				connecting = false;
				logger.error("PLC establishConnectionToPlc Error", e);
				logger.error("Waiting " + Math.pow(2, retryCount) + " seconds...");
				Thread.sleep((long) (Math.pow(2, retryCount) * 1000l));
				return establishConnectionToPlc(Math.min(++retryCount, 8));
			}
		}
		return false;

	}

	@Override
	public void run() {
		if (plcConnection == null) {
			logger.warn("Unkown Adpater: " + options.getString("connectionString") + "\n Canceling JOB ");
			future.cancel(false);
			return;
		}

		if (!plcConnection.isConnected() && backoff == 0) {
			try {
				establishConnectionToPlc(0);
			} catch (Exception e) {
				logger.error("Could not connect to Endpoint: " + options.getString("connectionString"));
				return;
			}
		}
		if (!plcConnection.isConnected()) {
			try {
				if (!connecting)
					establishConnectionToPlc(backoff);
				logger.warn("No connection to Data-Endpoint! " + backoff + ". try to reconnect... ");
				return;
			} catch (Exception e) {
				logger.error("PLC cannot read Error", e);
			}
		}

		try {

			if (plcConnection.canSubscribe()) {
				if (!this.subscribed) {
					for (JSONObject item : items) {
						plcConnection.subscribe(item, new PLCSubscriptionCallback() {

							@Override
							public void onNewData(OpenWareDataItem previous, OpenWareDataItem res) {
								OpenWareDataItem old = cache.get(res.getSource() + res.getId());
								cache.put(res.getSource() + res.getId(), res.cloneItem());
								try {
									if (res.getMeta().optBoolean("onChange")) {
										if (old != null && !old.equalsLastValue(res)) {
											send(res.getSource(), res.toString());
										}

									} else {
										send(res.getSource(), res.toString());
									}

								} catch (Exception e) {
									logger.error("PLC Subscription Error", e);

								}

							}
						});
					}
					this.subscribed = true;
				}
			} else {
				logger.info(options.getString("connectionString") + ": Reading " + items.size() + " item(s)");

				if (items.size() == 0) {
					logger.info("Nothing to do...skipping");
					return;
				}
				Map<JSONObject, OpenWareDataItem> result = plcConnection.readMultiple(items);

				logger.info("-----------------------");
				for (JSONObject item : result.keySet()) {
					OpenWareDataItem res = result.get(item);

					if (!processNewItem(item, res))
						logger.trace("Change-Threshold not reached for " + res.getSource() + "---" + res.getId());
					;
				}

				logger.info("-----------------------");

			}

		} catch (Exception e) {
			logger.error("PLC Loop Error", e);

		}

	}

	private boolean processNewItem(JSONObject item, OpenWareDataItem res) {
		OpenWareDataItem old = null;

		if (item.getJSONObject("extraOptions").has("useManualId")) {
			res.setId(item.getJSONObject("extraOptions").optString("useManualId"));
		}

		if (cache.size() > 0) {
			old = cache.get(res.getSource() + res.getId());
		}

		try {
			if (item.getJSONObject("extraOptions").has("scale")) {
				boolean hasFactor = item.getJSONObject("extraOptions").getJSONObject("scale").has("factor");
				boolean hasOffSet = item.getJSONObject("extraOptions").getJSONObject("scale").has("offset");
				try {
					double factor = hasFactor
							? item.getJSONObject("extraOptions").getJSONObject("scale").getDouble("factor")
							: 1.0;
					double offset = hasOffSet
							? item.getJSONObject("extraOptions").getJSONObject("scale").getDouble("offset")
							: 0.0;
					for (OpenWareValue value : res.value()) {
						for (int i = 0; i < value.size(); i++) {
							OpenWareValueDimension dim = value.get(i);
							if (dim instanceof OpenWareNumber) {
								logger.trace("Scaling retrieved value '" + dim.value().toString() + "' by " + factor);
								double newVal = ((double) dim.value() * factor) + offset;
								value.set(i, dim.createValueForDimension(newVal));
							}
						}
					}
				} catch (JSONException e) {
					logger.error("'Factor'-Parameter or 'offset'-Parameter is not a number value");
				}
			}

			boolean sendVal = true;
			if (item.optBoolean("onChange")) {
				if (item.getJSONObject("extraOptions").has("changeThreshold")) {
					JSONObject threshold = item.getJSONObject("extraOptions").getJSONObject("changeThreshold");
					if (old != null) {
						OpenWareValue oldVal = old.value().get(0);
						OpenWareValue newVal = res.value().get(0);
						if (oldVal.size() == newVal.size()) {
							boolean needsResend = false;
							for (int dim = 0; dim < oldVal.size(); dim++) {
								if (newVal.get(dim) instanceof OpenWareNumber
										&& oldVal.get(dim) instanceof OpenWareNumber) {
									double cOld = (double) oldVal.get(dim).value();
									double cNew = (double) newVal.get(dim).value();
									double cThreshold = threshold.optDouble("threshold");

									if (threshold.optString("type").toLowerCase().equals("absolute")) {
										needsResend = Math.abs(cThreshold) - (Math.abs(cOld - cNew)) < 0;
									} else {
										needsResend = cOld == 0 || Math.abs((cOld - cNew) / cOld) > cThreshold;

									}
									if (needsResend)
										break;
								}
							}
							sendVal = needsResend;
						}
					}
				} else {
					if ((old != null && old.equalsLastValue(res))) {
						sendVal = false;
					}
				}

			}
			if (sendVal) {
				cache.put(res.getSource() + res.getId(), res.cloneItem());
				send(res.getSource()+"/"+res.getId(), res.toString());
			}
			return sendVal;

		} catch (Exception e) {
			logger.error("PLC ReadMultipleError", e);

			return false;

		}
	}

	public void connectMQTT() throws MqttException, InterruptedException {

		if (options.getJSONObject("extraOptions").optString("host").equals("")) {
			logger.info("No host specified! No Mqtt Connection");
		}
		// Check if connection is already established or currently being established.
		// Return and do nothing if so.
		if (asyncClient != null && asyncClient.isConnected() && !mqttConnecting) {
			logger.warn("connectMQTT called while connection was already established.");
			return;
		}

		// Check if last try is more than 30 seconds ago. If so return and wait for next
		// call.
		if (System.currentTimeMillis() - mqttLastConnectTry < 30000) {
			logger.warn("connectMQTT called before reconnect waiting time has passed...Skipping connect.");
			return;
		}

		// Connection needs to be (re-) established
		mqttConnecting = true;
		mqttLastConnectTry = System.currentTimeMillis();

		asyncClient = new MqttAsyncClient("tcp://" + options.getJSONObject("extraOptions").getString("host"),
				options.getJSONObject("extraOptions").optString("clientId") + "_" + options.getString("objectId")+"_"+System.currentTimeMillis(),
				new MemoryPersistence());
		MqttConnectOptions opts = new MqttConnectOptions();

		if (options.getJSONObject("extraOptions").has("username")) {
			String user = options.getJSONObject("extraOptions").getString("username");
			char[] pw =options.getJSONObject("extraOptions").getString("password").toCharArray();
			opts.setUserName(user);
			opts.setPassword(pw);
		}

		opts.setMaxInflight(10000);
		asyncClient.connect(opts).waitForCompletion();
		prefix = options.getJSONObject("extraOptions").optString("prefix");
		suffix = options.getJSONObject("extraOptions").optString("suffix");
		mqttConnecting = false;

		send(prefix + options.getJSONObject("extraOptions").optString("clientId") + "_" + options.getString("objectId")
				+ "/hello" + suffix, "online");
		asyncClient.setCallback(new MqttCallback() {

			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				logger.trace("Got Message:\n" + "Topic: " + topic + "\nMessage: " + message);
			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
				// TODO Auto-generated method stub

			}

			@Override
			public void connectionLost(Throwable cause) {
				logger.error("Disconnected from MQTT", cause);
				try {
					connectMQTT();
				} catch (Exception e) {
					logger.warn("Could not connect to MQTT Broker\n" + e.getMessage());
				}

			}
		});

	}

	public void send(String topic, String message)
			throws MqttPersistenceException, MqttException, InterruptedException {
		System.out.print("Sending [" + topic + "]: " + message);
		long millis = System.currentTimeMillis();
		long currFloor = millis - (millis % 1000);
		if (lastTS != currFloor) {
			logger.trace("Rate ( based on " + count + " total messages) "
					+ (count / (double) ((currFloor / 1000) - (lastTS / 1000))) + " msg/s");
			count = 0;
			lastTS = currFloor;
		}
		count++;
		if (topic == null || message == null) {

			logger.error("Empty topic/message is not allowed and will not be published");
			return;
		}

		if (asyncClient != null && asyncClient.isConnected()) {
			MqttMessage msg = new MqttMessage();
			msg.setPayload(message.getBytes());

			asyncClient.publish(prefix + topic + suffix, msg);
			OpenConnect.getInstance().mqttLog.info(message);
			logger.info("[" + prefix + topic + suffix + "]" + message);

		} else {
			logger.warn("No Broker Connection! Message has not been published. Trying to reconnect");
			try {
				connectMQTT();
			} catch (Exception e) {
				logger.warn("Could not connect to MQTT Broker\n" + e.getMessage());
			}
		}

	}

	public void updateItems(List<JSONObject> newItems) {
		ArrayList<JSONObject> temp = new ArrayList<JSONObject>();
		Iterator<JSONObject> it = newItems.iterator();
		while (it.hasNext()) {
			JSONObject cItem = it.next();
			if (cItem.has("enabled") && cItem.getBoolean("enabled")) {
				temp.add(cItem);
			}
		}
		this.items = temp;
	}

	public ScheduledFuture<?> getFuture() {
		return future;
	}

	public void setFuture(ScheduledFuture<?> future) {
		this.future = future;
	}

}
