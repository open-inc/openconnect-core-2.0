package de.openinc.openconnect.configuration;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.openinc.openconnect.OpenConnect;

public class FileConnection implements Configurator {

	// Logging
	private static Logger logger = LoggerFactory.getLogger(OpenConnect.class);

	private JSONObject configData;
	private long lastModified;
	private String sessionToken;

	public FileConnection(String path) throws IllegalArgumentException {

		try {
			File configFile = new File(path);
			byte[] data = new byte[(int) configFile.length()];
			FileInputStream fis = new FileInputStream(configFile);
			fis.read(data);
			fis.close();
			configData = new JSONObject(new String(data, "UTF-8"));

			lastModified = configFile.lastModified();
		} catch (Exception e) {
			logger.error("File Constructor Error", e);
			e.printStackTrace();
		}
	}

	@Override
	public boolean login() {
		return configData != null;
	}

	@Override
	public List<JSONObject> getPLCDevices() {

		JSONArray devices = configData.getJSONArray("devices");
		ArrayList<JSONObject> toReturn = new ArrayList<JSONObject>();
		for (int i = 0; i < devices.length(); i++) {
			toReturn.add(devices.getJSONObject(i));
		}
		return toReturn;
	}

	@Override
	public List<JSONObject> getItemData() {
		List<JSONObject> res = new ArrayList<JSONObject>();
		for (int i = 0; i < configData.getJSONArray("items").length(); i++) {
			JSONObject currObj = configData.getJSONArray("items").getJSONObject(i);

			res.add(currObj);
		}
		return res;

	}

	@Override
	public List<JSONObject> getItemData(String deviceID) {

		List<JSONObject> res = new ArrayList<JSONObject>();
		for (int i = 0; i < configData.getJSONArray("items").length(); i++) {
			JSONObject currObj = configData.getJSONArray("items").getJSONObject(i);
			if (currObj.getJSONObject("DeviceID").getString("objectId").equals(deviceID)) {
				res.add(currObj);
			}
		}
		return res;
	}

	@Override
	public List<JSONObject> getChangedPLCDevices(long since) {
		if (lastModified > since) {
			return getPLCDevices();
		}
		return new ArrayList<>();
	}

}
