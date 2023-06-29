package de.openinc.openconnect.configuration;

import java.util.List;

import org.json.JSONObject;

public interface Configurator {

	boolean login();

	List<JSONObject> getPLCDevices();

	List<JSONObject> getChangedPLCDevices(long since);

	List<JSONObject> getItemData();

	List<JSONObject> getItemData(String deviceID);

}