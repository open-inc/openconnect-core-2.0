package de.openinc.openconnect.plcconnector;

import java.util.concurrent.Future;

import org.json.JSONObject;

public interface WriteAdapter {
	public Future<String> writeData(JSONObject target, String topic, String payload);

	public void connect(JSONObject options) throws Exception;

	public boolean isConnected();

	public void disconnect() throws Exception;

}
