package de.openinc.openconnect.plcconnector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import de.openinc.openconnect.OpenConnect;
import de.openinc.openconnect.configuration.Configurator;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

public class PLCConfigurationRunnable implements Runnable {

	// Logging
	private static Logger logger = LoggerFactory.getLogger(OpenConnect.class);
	private SimpleDateFormat datetimeformat = new SimpleDateFormat("dd.MM.yyyy kk:ss:SS X");
	private long lastUpdated;
	private ArrayList<JSONObject> restartLater;
	private Configurator config;

	private String heartbeatEndpoint;

	public PLCConfigurationRunnable(Configurator config, String heartbeatEndpoint) throws Exception {
		restartLater = new ArrayList<JSONObject>();
		lastUpdated = System.currentTimeMillis();
		this.config = config;
		this.heartbeatEndpoint = heartbeatEndpoint;
	}

	@Override
	public void run() {
		logger.info("Checking for config updates since " + datetimeformat.format(new Date(lastUpdated)));
		try {

			if (restartLater.size() > 0) {
				Iterator<JSONObject> it = restartLater.iterator();
				while (it.hasNext()) {
					JSONObject cDev = it.next();
					try {
						OpenConnect.getInstance().startReadingForDevice(cDev);
						it.remove();
					} catch (Exception e) {
						continue;
					}
				}

			}

			if (this.heartbeatEndpoint != null && this.heartbeatEndpoint.trim().length() > 0) {
				logger.info("Sending Heartbeat to monitoring service.");
				int status = 0;
				try {
					GetRequest req = Unirest.get(this.heartbeatEndpoint);
					HttpResponse<JsonNode> node = req.asJson();
					status = node.getStatus();
					JSONObject o = new JSONObject(node.getBody().getObject().toString());
					if (o != null && status == 200) {
						if (!o.getBoolean("ok")) {
							logger.info("'Uptime Kuma' not available - has no impact on open.CONNECT performance!");
						}
					}
				} catch (Exception e) {
					OpenConnect.getInstance().logger.info("Connection to 'Uptime Kuma' failed with status " + status
							+ " - has no impact on open.CONNECT performance!");
				}
			}

			List<JSONObject> changedDevs = config.getChangedPLCDevices(lastUpdated);
			lastUpdated = System.currentTimeMillis();
			if (changedDevs == null)
				return;
			ArrayList<String> alreadyRestarted = new ArrayList<String>();
			for (JSONObject dev : changedDevs) {
				Runnable r = OpenConnect.getInstance().plcThreads.get(dev.get("objectId"));
				if (r != null) {
					PLCReaderRunnable plcR = (PLCReaderRunnable) r;
					try {
						plcR.cancel();
					} catch (Exception e) {
						logger.error("Error while stopping " + dev.getString("name"), e);
					}
				}
				alreadyRestarted.add(dev.getString("objectId"));

			}
			Thread.sleep(5000);
			for (JSONObject dev : changedDevs) {
				if (dev.getBoolean("enabled")) {
					try {
						OpenConnect.getInstance().startReadingForDevice(dev);
					} catch (Exception e) {
						logger.error("Error while starting " + dev.getString("name"), e);
						restartLater.add(dev);
					}
				}

			}

		} catch (Exception e) {
			logger.error("Error while updating configuration", e);
		}
	}

}
