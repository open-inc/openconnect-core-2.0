package de.openinc.openconnect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import de.openinc.openconnect.configuration.Configurator;
import de.openinc.openconnect.configuration.FileConnection;
import de.openinc.openconnect.configuration.ParseConnection;
import de.openinc.openconnect.pi.MailMessenger;
import de.openinc.openconnect.pi.SyncServiceInterface;
import de.openinc.openconnect.pi.requesthandling.RequestHandlingFactory;
import de.openinc.openconnect.plcconnector.PLCAdapter;
import de.openinc.openconnect.plcconnector.PLCConfigurationRunnable;
import de.openinc.openconnect.plcconnector.PLCReaderRunnable;
import de.openinc.openconnect.plcconnector.PluginRoute;
import de.openinc.openconnect.plcconnector.WriteAdapter;
import de.openinc.openconnect.serivces.ServiceInterface;
import de.openinc.openconnect.serivces.ServiceProvider;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;

public class OpenConnect {

	public Dotenv props;

	// Capabilities
	private String OC_CAP_FLAGS = "";

	// Parse
	private String PARSE_HOST = "";
	private String PARSE_USER = "";
	private String PARSE_PW = "";
	private String PARSE_APPID = "";

	// Mail
	private Boolean MAIL_ENABLE = false;
	private Boolean MAIL_STARTTLS = false;
	private String MAIL_HOST = "";
	private String MAIL_PORT = "";
	private String MAIL_SSL_TRUST = "";
	private String MAIL_USERNAME = "";
	private String MAIL_PASSWORD = "";
	private String MAIL_FROM_ADDRESS = "";
	private String MAIL_TO_ADDRESS = "";
	private Boolean MAIL_SMTP_SSL_ENABLE = false;

	// File
	private boolean offlineUse = false;

	// Heartbeat Service
	private String heartbeatEndpoint = "";

	// Item and Prefix
	private String USER_PREFIX = "";

	// Threadpool
	private ScheduledExecutorService executorService;
	public HashMap<String, Runnable> plcThreads;

	// Logging
	public Logger logger = LoggerFactory.getLogger("rootLogger");
	public Logger mqttLog = LoggerFactory.getLogger("mqttLogger");
	// API services
	private HashMap<String, SyncServiceInterface> registeredServices = new HashMap<String, SyncServiceInterface>();
	public RequestHandlingFactory requestfactory;

	static ArrayList<String> items = new ArrayList<String>();
	static ArrayList<String> dbs = new ArrayList<String>();

	public Configurator configurator;
	public JWTVerifier verifier;
	private static OpenConnect me;

	private OpenConnect() {
	}

	public void init() throws NumberFormatException, Exception {
		logger.info("----------------- Start Programm!");
		new ServiceProvider();

		if (!loadConfig()) {
			logger.error("Error - Loading Config");
			System.out.println("---------------------------------------------- Error - Loading Config");
			return;
		}
		executorService = Executors.newScheduledThreadPool(Integer.valueOf(props.get("OC_THREAD_POOLSIZE", "4")));

		if (!login()) {
			logger.error("Error - Login");
			System.out.println("------------------------------------------ Error - Login");
			return;
		}

		if (OC_CAP_FLAGS.toLowerCase().contains("plc")) {
			logger.info("PLC starting");
			startReading();
		}

		if (OC_CAP_FLAGS.toLowerCase().contains("api")) {
			logger.info("API starting");
			initWebApi(Integer.valueOf(props.get("OC_WEBAPI_PORT", "80")));
		}

		if (MAIL_ENABLE) {
			logger.info("Mail starting");
			initMailing();
		}
	}

	public static OpenConnect getInstance() {
		try {
			if (me == null) {
				me = new OpenConnect();
			}
		} catch (Exception e) {
			System.err.println("Could not start open.CONNECT");
			e.printStackTrace();
			return null;
		}

		return me;
	}

	public boolean loadConfig() {
		// Load Config File

		try {
			logger.info("Using ENV configuration");
			props = Dotenv.load();
			logger.info("Configuration file loaded");

			// Capabilities
			OC_CAP_FLAGS = props.get("OC_CAP_FLAGS");

			// Parse
			PARSE_HOST = props.get("OC_PARSE_HOST");
			PARSE_USER = props.get("OC_PARSE_USER");
			PARSE_PW = props.get("OC_PARSE_PASSWORD");
			PARSE_APPID = props.get("OC_PARSE_APPID");
			offlineUse = Boolean.valueOf(props.get("OC_OFFLINE", "false"));

			// Mail config
			MAIL_ENABLE = Boolean.valueOf(props.get("MAIL_ENABLE", "false"));
			MAIL_STARTTLS = Boolean.valueOf(props.get("MAIL_STARTTLS", "false"));
			MAIL_HOST = props.get("MAIL_HOST", "");
			MAIL_PORT = props.get("MAIL_PORT", "");
			MAIL_SSL_TRUST = props.get("MAIL_SSL_TRUST", "");
			MAIL_USERNAME = props.get("MAIL_USERNAME", "");
			MAIL_PASSWORD = props.get("MAIL_PASSWORD", "");
			MAIL_FROM_ADDRESS = props.get("MAIL_FROM_ADDRESS", "");
			MAIL_TO_ADDRESS = props.get("MAIL_TO_ADDRESS", "");
			MAIL_SMTP_SSL_ENABLE = Boolean.valueOf(props.get("MAIL_SMTP_SSL_ENABLE", "false"));

			// Heartbeat
			heartbeatEndpoint = props.get("OC_UPTIME_KUMA_HEARTBEAT");

			logger.info("Configuration applied");
			return true;
		} catch (Exception e) {
			logger.error("Configuration file exception!", e);
			e.printStackTrace();
			return false;
		}
	}

	public boolean login() {
		if (!offlineUse) {
			configurator = new ParseConnection(PARSE_HOST, PARSE_USER, PARSE_PW, PARSE_APPID);
		} else {
			configurator = new FileConnection(props.get("OC_OFFLINE_FILE"));
		}
		return configurator.login();
	}

	private void initWebApi(int port) {

		requestfactory = new RequestHandlingFactory();
		Algorithm algorithm = Algorithm.HMAC256(props.get("OC_WEBAPI_SECRET", "THIS_BETTER_BETTER_IS_CHANGED_FAST"));
		verifier = JWT.require(algorithm).build();

		Javalin app = Javalin.create(config -> {
			config.plugins.enableDevLogging();
			config.plugins.enableRouteOverview("/openconnect/routes");
		});

		app.before("*", ctx -> {
			logger.info("-------------------");
			logger.info("Received call from " + ctx.ip() + " to " + ctx.method() + ":" + ctx.fullUrl());
			logger.info("-Headers-");
			logger.info(new JSONObject(ctx.headerMap()).toString());
			logger.info("-Body-");
			logger.info(ctx.body());
			logger.info("-------------------");
		});

		app.before("/write/*", ctx -> {

			String authToken = ctx.headerMap().getOrDefault("Authorization", "").replace("Bearer ", "");

			try {
				DecodedJWT jwt = verifier.verify(authToken);
				String sub = jwt.getSubject();
				ctx.sessionAttribute("subject", sub);
			} catch (JWTVerificationException e) {
				throw new ForbiddenResponse("You need to be authenticated to use this api");
			}

		});

		app.before("/openconnect/*", ctx -> {

			String authToken = ctx.headerMap().getOrDefault("Authorization", "").replace("Bearer ", "");

			try {
				DecodedJWT jwt = verifier.verify(authToken);
				String sub = jwt.getSubject();
				ctx.sessionAttribute("subject", sub);
			} catch (JWTVerificationException e) {
				throw new ForbiddenResponse("You need to be authenticated to use this api");
			}

		});

		app.before("/adapters/*", ctx -> {

			String authToken = ctx.headerMap().getOrDefault("Authorization", "").replace("Bearer ", "");

			try {
				DecodedJWT jwt = verifier.verify(authToken);
				String sub = jwt.getSubject();
				ctx.sessionAttribute("subject", sub);
			} catch (JWTVerificationException e) {
				throw new ForbiddenResponse("You need to be authenticated to use this api");
			}

		});

		app.post("/write/{target}/{topic}", ctx -> {
			Map<String, Object> response = new HashMap<String, Object>();
			String deviceid = ctx.pathParam("target");
			String subRegEx = ctx.sessionAttribute("subject");
			if (!Pattern.matches(subRegEx, deviceid)) {
				throw new ForbiddenResponse("You are not allowed to write to device " + deviceid);
			}
			Optional<JSONObject> deviceOpt = configurator.getPLCDevices().stream()
					.filter(t -> t.getString("objectId").equals(deviceid)).findFirst();
			if (!deviceOpt.isPresent()) {
				throw new BadRequestResponse("The device with id:" + deviceid + " is not configured as target device");
			}
			JSONObject device = deviceOpt.get();

			WriteAdapter wa = null;
			PLCAdapter writeservice = ServiceProvider
					.getPlcAdapterFromConnectionString(device.getString("connectionString"));
			if (writeservice instanceof WriteAdapter) {
				wa = (WriteAdapter) writeservice;
			}
			if (wa == null) {
				throw new BadRequestResponse("The device with id:" + deviceid
						+ " is misconfigured (no implementation of protocol available).");
			}

			try {
				wa.connect(device);
				String res = wa.writeData(device, ctx.pathParam("topic"), ctx.body()).get();
				response.put("result", res);
				ctx.json(response);
			} catch (Exception e) {
				throw new InternalServerErrorResponse("Could not connect to target device:" + e.getMessage());
			}
		});

		app.routes(() -> {
			io.javalin.apibuilder.ApiBuilder.path("adapters", () -> {
				for (ServiceInterface service : ServiceProvider.getAllRouteImplementations()) {
					if (service == null)
						continue;
					io.javalin.apibuilder.ApiBuilder.path(service.getAdapterId(), () -> {

						// Initialize the SyncService with options from file/parse
						if (service instanceof SyncServiceInterface) {
							logger.info("Init service " + service.getAdapterId() + " with options.");
							Map<String, Object> options = new HashMap<String, Object>();
							List<JSONObject> devices = configurator.getPLCDevices();

							// Search for the fitting device config based on the loaded plc devices
							for (int i = 0; i < devices.size(); i++) {
								if (devices.get(i).getString("connectionString").startsWith(service.getAdapterId())) {
									// Found the correct device object
									JSONObject optionsFromDeviceConfig = devices.get(i).getJSONObject("options");
									Iterator<String> optionkeys = optionsFromDeviceConfig.keys();
									// Get all options and put to options map
									while (optionkeys.hasNext()) {
										String key = optionkeys.next();
										options.put(key, optionsFromDeviceConfig.getString(key));
									}
								}
							}
							if (((SyncServiceInterface) service).init(options)) {
								logger.info("SyncServiceInterface " + service.getAdapterId()
										+ " successfully initialized.");
								this.registeredServices.put(service.getAdapterId(), (SyncServiceInterface) service);
							}
						}
						if (service instanceof PluginRoute) {
							logger.info("PluginRoute " + service.getAdapterId() + " successfully initialized.");
							((PluginRoute) service).registerRoutes();
						}

					});
				}
			});
		});

		app.get("/openconnect/status", (Context ctx) -> {
			JSONObject status = new JSONObject();

			JSONArray jsonServices = new JSONArray();
			// Get all services which have been initialized
			this.registeredServices.forEach((key, value) -> {
				JSONObject service = new JSONObject();
				service.put("key", key);
				service.put("adapterID", value.getAdapterId());
				service.put("type", value.getType());
				jsonServices.put(service);
			});
			status.put("LoadedServices", jsonServices);

			// Get all request implementations
			JSONArray jsonRequests = new JSONArray();
			this.requestfactory.getAllRequestCases().forEach((key, value) -> {
				JSONObject request = new JSONObject();
				request.put("key", key);
				request.put("from", value.fromService());
				request.put("to", value.toService());
				request.put("type", value.typeRequest());
				request.put("httpMethod", value.httpMethod());
				jsonRequests.put(request);
			});
			status.put("requestImplementations", jsonRequests);

			ctx.result(status.toString(4));
		});

		app.start(port);

		logger.info("Server started and listening on port " + port);
	}

	private void initMailing() {
		MailMessenger.getInstance().init(MAIL_STARTTLS, MAIL_HOST, MAIL_PORT, MAIL_SSL_TRUST, MAIL_USERNAME,
				MAIL_PASSWORD, MAIL_FROM_ADDRESS, MAIL_TO_ADDRESS, MAIL_SMTP_SSL_ENABLE);
	}

	public boolean startReading() throws NumberFormatException, Exception {
		plcThreads = new HashMap<String, Runnable>();

		List<JSONObject> connectionStrings = configurator.getPLCDevices();
		if (connectionStrings == null)
			return false;
		for (int i = 0; i < connectionStrings.size(); i++) {
			try {
				startReadingForDevice(connectionStrings.get(i));
			} catch (Exception e) {
				logger.error("PLC Reading Error ", e);
				continue;
			}

		}

		executorService.scheduleAtFixedRate(new PLCConfigurationRunnable(configurator, heartbeatEndpoint), 0l,
				Long.valueOf(props.get("OC_REFRESH_INTERVAL", "60000")), TimeUnit.MILLISECONDS);

		return plcThreads.keySet().size() > 0;

	}

	public void startReadingForDevice(JSONObject plcDevice) throws Exception {
		if (!plcDevice.getBoolean("enabled"))
			return;
		String connectionString = plcDevice.getString("connectionString");
		String objectid = plcDevice.getString("objectId");
		logger.info("Starting Reading Thread for " + connectionString);
		System.out.println("Starting Reading Thread for " + connectionString);

		PLCReaderRunnable runnable = new PLCReaderRunnable(plcDevice, USER_PREFIX);
		runnable.updateItems(configurator.getItemData(objectid));

		runnable.setFuture(executorService.scheduleAtFixedRate(runnable, 0l, plcDevice.getLong("interval"),
				TimeUnit.MILLISECONDS));

		plcThreads.put(objectid, runnable);
	}

	public void stopReading() {
		logger.info("--------Stopping Reading----------");
		executorService.shutdown();
		executorService = Executors.newScheduledThreadPool(Integer.valueOf(props.get("OC_THREAD_POOLSIZE", "4")));
		logger.info("--------Reading stopped----------");
	}

}
