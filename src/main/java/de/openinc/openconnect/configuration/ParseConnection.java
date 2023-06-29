package de.openinc.openconnect.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import de.openinc.openconnect.OpenConnect;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

public class ParseConnection implements Configurator {

	// Logging
	private static Logger logger = LoggerFactory.getLogger(OpenConnect.class);

	private HashMap<String, String> headers;

	private String HOST;
	private String user;
	private String pw;
	private String appid;

	private String sessionToken;

	public ParseConnection(String _host, String _user, String _pw, String _appid) throws IllegalArgumentException {
		HOST = _host;
		user = _user;
		pw = _pw;
		appid = _appid;

		try {
			this.headers = new HashMap<>();
			if (HOST == null || user == null || pw == null || appid == null) {
				throw new IllegalArgumentException(
						"Parse Property file incorrect. Please provide a host and credentials");
			}

			this.prepareHttpsClient();

		} catch (Exception e) {
			logger.error("Parse Constructor Error", e);
			e.printStackTrace();
		}
	}

	protected void prepareHttpsClient() {
	/*
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		try {
			String certificateStorage = "cacerts";
			String certificatePassword = "openinc";
			File certfile = new File(certificateStorage);
			if (certfile.exists()) {
				SSLContext sslContext = SSLContexts.custom()
						.loadTrustMaterial(certfile, certificatePassword.toCharArray(), new TrustSelfSignedStrategy())
						.build();
				SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext,
						new String[] { "TLSv1.3" }, null, SSLConnectionSocketFactory.getDefaultHostnameVerifier());
				clientBuilder.setSSLSocketFactory(sslFactory);
			}
		} catch (Exception e) {
			logger.error("Certificate Error", e);
		}

		HttpClient httpClient = clientBuilder.build();
		
		Unirest.setHttpClient(httpClient);
		*/
	}

	public boolean login() {
		try {
			File sessionFile = new File("session");
			if (sessionFile.exists()) {
				try {
					byte[] data = new byte[(int) sessionFile.length()];
					FileInputStream fis = new FileInputStream(sessionFile);
					fis.read(data);
					fis.close();
					String str = new String(data, "UTF-8");
					sessionToken = str;
					fis.close();
				} catch (IOException e) {
					logger.error("Parse Login Error", e);
					// TODO Auto-generated catch block
					e.printStackTrace();

				}

			} else {
				HttpResponse<JsonNode> res2 = Unirest
						.get(HOST + "/login?username=" + user.replace(" ", "") + "&password=" + pw.replace(" ", ""))
						.header("X-Parse-Application-Id", appid).header("X-Parse-Revocable-Session", "1").asJson();

				JSONObject response2 = new JSONObject(res2.getBody().getObject().toString());

				sessionToken = response2.getString("sessionToken");
				FileWriter myWriter = new FileWriter("session");
				myWriter.write(sessionToken);
				myWriter.close();
			}
			return true;

		} catch (UnirestException e) {
			logger.error("Parse Login Error (UniRest)", e);
			return false;
		} catch (JSONException e1) {
			logger.error("Parse Login Error (JSON)", e1);
			return false;
		} catch (IOException e) {
			logger.error("Parse Login Error (IO)", e);
			return false;
		}
	}

	public List<JSONObject> getPLCDevices() {
		HttpResponse<JsonNode> res;
		List<JSONObject> devices = new ArrayList<JSONObject>();
		try {
			res = Unirest.get(HOST + "/classes/OWPlcDevice").header("X-Parse-Application-Id", appid)
					.header("X-Parse-Session-Token", sessionToken).asJson();

			JSONArray response = new JSONArray(res.getBody().getObject().getJSONArray("results").toString());
			for (int i = 0; i < response.length(); i++) {
				JSONObject cDevice = response.getJSONObject(i);
				devices.add(cDevice);
			}
			return devices;
		} catch (UnirestException e) {
			logger.error("Parse getPLCDevices Error (UniRest)", e);
			return null;
		}
	}

	public List<JSONObject> getItemData() {
		HttpResponse<JsonNode> res;
		List<JSONObject> response = new ArrayList<JSONObject>();
		try {

			boolean getMore = true;
			int limitPuffer = 0;
			while (getMore) {
				res = Unirest.get(HOST + "/classes/OWPlcItem?limit=100&skip=" + limitPuffer + "&include=source")
						.header("X-Parse-Application-Id", appid).header("X-Parse-Session-Token", sessionToken).asJson();

				JSONArray responsePuffer = new JSONArray(res.getBody().getObject().getJSONArray("results").toString());
				for (int i = 0; i < responsePuffer.length(); i++) {
					response.add(responsePuffer.getJSONObject(i));
				}
				if (responsePuffer.length() == 100) {
					limitPuffer = limitPuffer + 100;
				} else {
					getMore = false;
				}

			}

			return response;
		} catch (UnirestException e) {
			logger.error("Parse getItemData Error (UniRest)", e);
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public List<JSONObject> getItemData(String deviceID) {
		HttpResponse<JsonNode> res;
		List<JSONObject> response = new ArrayList<JSONObject>();
		JSONObject x = new JSONObject();
		JSONObject pointer = new JSONObject();
		pointer.put("__type", "Pointer");
		pointer.put("className", "OWPlcDevice");
		pointer.put("objectId", deviceID);
		x.put("DeviceID", pointer);

		try {
			boolean getMore = true;
			int limitPuffer = 0;
			while (getMore) {
				res = Unirest.get(HOST + "/classes/OWPlcItem?limit=100&skip=" + limitPuffer + "&include=source")
						.header("X-Parse-Application-Id", appid).header("X-Parse-Session-Token", sessionToken)
						.queryString("where", x.toString()).asJson();

				JSONArray responsePuffer = new JSONArray(res.getBody().getObject().getJSONArray("results").toString());
				for (int i = 0; i < responsePuffer.length(); i++) {
					response.add(responsePuffer.getJSONObject(i));
				}
				if (responsePuffer.length() == 100) {
					limitPuffer = limitPuffer + 100;
				} else {
					getMore = false;
				}

			}
			return response;
		} catch (UnirestException e) {
			logger.error("Parse getItemData Error (UniRest)", e);
			return null;
		}
	}

	@Override
	public List<JSONObject> getChangedPLCDevices(long since) {
		List<JSONObject> devices = getPLCDevices();
		Iterator<JSONObject> it = devices.iterator();
		while (it.hasNext()) {
			JSONObject cObj = it.next();
			DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
			OffsetDateTime offsetDateTime = OffsetDateTime.parse(cObj.getString("updatedAt"), timeFormatter);
			Date date = Date.from(Instant.from(offsetDateTime));
			if (date.getTime() < since && getChangedItemData(since, cObj.getString("objectId")).size() == 0) {
				it.remove();
			}
		}
		return devices;
	}

	private List<JSONObject> getChangedItemData(long since, String deviceId) {
		List<JSONObject> items = getItemData();
		Iterator<JSONObject> it = items.iterator();
		while (it.hasNext()) {
			JSONObject cObj = it.next();
			if (!cObj.getJSONObject("DeviceID").getString("objectId").equals(deviceId)) {
				it.remove();
				continue;
			}
			DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
			OffsetDateTime offsetDateTime = OffsetDateTime.parse(cObj.getString("updatedAt"), timeFormatter);
			Date date = Date.from(Instant.from(offsetDateTime));
			if (date.getTime() < since) {
				it.remove();
			}
		}
		return items;
	}

}
