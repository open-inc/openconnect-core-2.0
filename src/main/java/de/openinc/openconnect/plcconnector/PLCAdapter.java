package de.openinc.openconnect.plcconnector;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import de.openinc.model.data.OpenWareDataItem;
import de.openinc.openconnect.serivces.ServiceInterface;

public interface PLCAdapter extends ServiceInterface {

	public String getAdapterId();

	public JSONObject getConfig() throws Exception;

	public void connect(JSONObject options) throws Exception;

	public boolean isConnected();

	public void disconnect() throws Exception;

	public Map<JSONObject, OpenWareDataItem> readMultiple(List<JSONObject> adresses) throws Exception;

	public void subscribe(JSONObject address, PLCSubscriptionCallback callback);

	public boolean canSubscribe();
}