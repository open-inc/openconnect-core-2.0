package de.openinc.openconnect.adapters.mobility;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;



import de.openinc.model.data.OpenWareDataItem;
import de.openinc.openconnect.OpenConnect;
import de.openinc.openconnect.plcconnector.PLCAdapter;
import de.openinc.openconnect.plcconnector.PLCSubscriptionCallback;
import kong.unirest.GetRequest;

public class MDSAdapter implements PLCAdapter {
	String endpoint;
	String suffixJSON = "";
	private boolean canConnect = false;
	private HashMap<String, Long> geographiesLastUpdate;
	private Map<String, String> header;
	private long interval;
	private JSONObject device;
	private GetRequest req;
	private Object object;

	public MDSAdapter() {
		geographiesLastUpdate = new HashMap<String, Long>();
	}

	@Override
	public String getAdapterId() {
		// TODO Auto-generated method stub
		return "mds.http";
	}

	@Override
	public void connect(JSONObject options) throws Exception {
		endpoint = options.getString("connectionString").substring(4);
		interval = options.getLong("interval");
		device = options;
		header = new HashMap<String, String>();
		if (options.has("options") && options.getJSONObject("options").has("header")) {
			Map<String, Object> cHeaders = options.getJSONObject("options").getJSONObject("header").toMap();
			for (String key : cHeaders.keySet()) {
				header.put(key, cHeaders.get(key).toString());
			}
		}

		if (options.getJSONObject("extraOptions").optBoolean("suffixJSON")) {
			suffixJSON = ".json";
		}

		int status = 0;

		/*
		 * try { req = Unirest.get(endpoint).headers(header); HttpResponse<JsonNode>
		 * node =req.asJson(); status = node.getStatus(); JSONObject o =
		 * node.getBody().getObject(); if(o!=null && status==200) { canConnect = true; }
		 * }catch(Exception e) { App.logger.error("MDS connection failed with status " +
		 * status, e); canConnect = false; }
		 */
		canConnect = true;
	}

	@Override
	public boolean isConnected() {
		return canConnect;
	}

	@Override
	public Map<JSONObject, OpenWareDataItem> readMultiple(List<JSONObject> adresses) throws Exception {
		HashMap<JSONObject, OpenWareDataItem> result = new HashMap<JSONObject, OpenWareDataItem>();
		;

		for (JSONObject item : adresses) {
			switch (item.getString("address")) {
			case "geographies": {
				OpenWareDataItem candidate = getGeographies(item);
				if (candidate.value().size() > 0)
					result.put(item, candidate);
				break;
			}

			default:
				break;
			}
		}

		return result;
	}

	private OpenWareDataItem getGeographies(JSONObject item) throws Exception {
		/*
		 * GetRequest req =
		 * Unirest.get(endpoint+"/geographies"+suffixJSON).headers(header); JSONObject
		 * res = req.asJson().getBody().getObject(); long now =
		 * System.currentTimeMillis(); long updated = res.optLong("updated");
		 * if(geographiesLastUpdate.getOrDefault(device.getString("objectId")+"::"+item.
		 * getString("objectId"), 0l)<updated) { return null; }
		 * geographiesLastUpdate.put(device.getString("objectId")+"::"+item.getString(
		 * "objectId"), updated);
		 * 
		 * for(int i=0; i<res.getJSONArray("geographies").length(); i++) { String
		 * stationid =
		 * res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).optString(
		 * "station_id"); if(stationid.equals("") && !ignoreTripsWithoutStationInfo) {
		 * double lat=
		 * res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getDouble(
		 * "lat"); double lon =
		 * res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getDouble(
		 * "lon"); stationid = virtualStationId(lat, lon); }
		 * res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put(
		 * "station_id", stationid);
		 * res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put(
		 * "lastSeen", now); }
		 * 
		 * String itemid = device.getString("objectId")+"::"+item.getString("objectId");
		 * String source = item.getString("OWSource"); ArrayList<OpenWareValue> values =
		 * new ArrayList<OpenWareValue>(); ArrayList<OpenWareValueDimension> dims = new
		 * ArrayList<OpenWareValueDimension>(); long lastTS = 0l; //OpenWareDataItem
		 * locations = new OpenWareDataItem(item.getString("objectId"),
		 * item.getString("OWSource"), item.getString("label"), item, vTypes); for(int
		 * i=0; i<cVehicles.length(); i++) { JSONObject cVehicle =
		 * cVehicles.getJSONObject(i); JSONObject oVehicle =
		 * vehicles.get(cVehicle.getString("bike_id"));
		 * 
		 * if(oVehicle==null) continue;
		 * 
		 * String station_start = oVehicle.getString("station_id");
		 * if(station_start.equals("") && ignoreTripsWithoutStationInfo) continue;
		 * 
		 * long rentalTime = cVehicle.optLong("lastSeen")-oVehicle.optLong("lastSeen");
		 * 
		 * String station_now = cVehicle.getString("station_id");
		 * if(station_now.equals("") && ignoreTripsWithoutStationInfo) continue;
		 * 
		 * boolean isSameStation = station_start.substring(0,
		 * Math.min(station_start.length(),
		 * TRIP_PRECISION)).equals(station_now.subSequence(0,
		 * Math.min(station_now.length(), TRIP_PRECISION)));
		 * if(!((rentalTime)>interval+60000 || !isSameStation)) { continue; }
		 * OpenWareNumber rentalDuration = new
		 * OpenWareNumber(item.optString("label")+" - "+"Fahrzeit", "ms",
		 * (double)rentalTime); OpenWareGeo pos_start = new
		 * OpenWareGeo(item.optString("label")+" - "+"Letzte Position",
		 * "",generateGeoPoint(oVehicle.optDouble("lat"), oVehicle.optDouble("lon"),
		 * oVehicle)); OpenWareGeo pos_end = new
		 * OpenWareGeo(item.optString("label")+" - "+"Aktuelle Position", "",
		 * generateGeoPoint(cVehicle.optDouble("lat"), cVehicle.optDouble("lon"),
		 * oVehicle)); OpenWareString stat_start= new
		 * OpenWareString(item.optString("label")+" - "+"Letzte Station",
		 * "",station_start ); OpenWareString stat_end= new
		 * OpenWareString(item.optString("label")+" - "+"Aktuelle Station", "",
		 * station_now); OpenWareString vehicleid = new
		 * OpenWareString(item.optString("label")+" - "+"Fahrzeug","",
		 * cVehicle.getString("bike_id"));
		 * 
		 * if(dims.size()==0) { dims.add(rentalDuration); dims.add(stat_end);
		 * dims.add(pos_end); dims.add(stat_start); dims.add(pos_start);
		 * dims.add(vehicleid); } OpenWareValue val = new
		 * OpenWareValue(cVehicle.optLong("lastSeen")+i); val.add(rentalDuration);
		 * val.add(stat_end); val.add(pos_end); val.add(stat_start); val.add(pos_start);
		 * val.add(vehicleid); values.add(val); } OpenWareDataItem res = new
		 * OpenWareDataItem(itemid,source,device.optString("name"),item, dims);
		 * res.value(values); return res;
		 */
		return null;
	}

	private JSONObject generateGeoPoint(double lat, double lon, JSONObject props) {
		JSONObject geoJSON = new JSONObject("{\r\n" + "  \"type\": \"Feature\",\r\n" + "  \"geometry\": {\r\n"
				+ "    \"type\": \"Point\",\r\n" + "    \"coordinates\": [" + lon + "," + lat + "]\r\n" + "  }"

				+ "}");
		if (props != null) {
			geoJSON.put("properties", props);
		}
		return geoJSON;
	}

	private String virtualStationId(double lat, double lon) {
		// 7 Digit precision ~ 75m
		//
		// https://duyanghao.github.io/geohash/
		return Geohash.encode(lat, lon, 12);
	}

	@Override
	public void subscribe(JSONObject address, PLCSubscriptionCallback callback) {
		OpenConnect.getInstance().logger.warn("[MDSCONNECTOR] Subscribe has been triggered, but it is not supported by adapter");

	}

	@Override
	public void disconnect() throws Exception {

	}

	@Override
	public boolean canSubscribe() {

		return false;
	}

	@Override
	public JSONObject getConfig() throws Exception {
		String configNamespace = "/" + this.getClass().getPackageName().replace(".", "/") + "/config.json";
		InputStream input = this.getClass().getResourceAsStream(configNamespace);
		StringBuilder sb = new StringBuilder();
		for (int ch; (ch = input.read()) != -1; ) {
		    sb.append((char) ch);
		}
		return new JSONObject(sb.toString());
	}

}
