package de.openinc.openconnect.adapters.mobility;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.collect.Lists;


import de.openinc.model.data.OpenWareDataItem;
import de.openinc.model.data.OpenWareGeo;
import de.openinc.model.data.OpenWareNumber;
import de.openinc.model.data.OpenWareString;
import de.openinc.model.data.OpenWareValue;
import de.openinc.model.data.OpenWareValueDimension;
import de.openinc.openconnect.OpenConnect;
import de.openinc.openconnect.plcconnector.PLCAdapter;
import de.openinc.openconnect.plcconnector.PLCSubscriptionCallback;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

public class GBFSAdapter implements PLCAdapter {
	String endpoint;
	String suffixJSON = "";
	private boolean canConnect = false;
	private HashMap<String, JSONObject> vehicles;
	private long fullsyncthreshold = 1000l * 60l * 60l;
	private long lastSync = 0l;
	private Map<String, String> header;
	private long interval;
	private JSONObject device;
	private GetRequest req;
	private Object object;
	private boolean ignoreTripsWithoutStationInfo;
	private int lastVehicleCount;

	public GBFSAdapter() {
		vehicles = new HashMap<String, JSONObject>();
		lastVehicleCount = 0;
	}

	@Override
	public String getAdapterId() {
		return "gbfs.http";
	}

	@Override
	public void connect(JSONObject options) throws Exception {
		endpoint = options.getString("connectionString").substring(5);
		interval = options.getLong("interval");
		device = options;
		ignoreTripsWithoutStationInfo = false;
		
		header = new HashMap<String, String>();
		if (options.has("options") && options.getJSONObject("options").has("header")) {
			Map<String, Object> cHeaders = options.getJSONObject("options").getJSONObject("header").toMap();
			for (String key : cHeaders.keySet()) {
				header.put(key, cHeaders.get(key).toString());
			}
		}

		try {
			fullsyncthreshold = options.getJSONObject("options").getLong("fullsyncthreshold");
		} catch (Exception e) {
			OpenConnect.getInstance().logger.info("No Full Sync Threshold set. Defaulting to 1h");
		}

		if (options.getJSONObject("extraOptions").optBoolean("suffixJSON")) {
			suffixJSON = ".json";
		}
		if (options.getJSONObject("extraOptions").optBoolean("ignoreTripsWithoutStationInfo")) {
			ignoreTripsWithoutStationInfo = true;
		}

		int status = 0;

		try {
			req = Unirest.get(endpoint).headers(header);
			HttpResponse<JsonNode> node = req.asJson();
			status = node.getStatus();
			JSONObject o = new JSONObject(node.getBody().getObject().toString());
			if (o != null && status == 200) {
				canConnect = true;
			}
		} catch (Exception e) {
			OpenConnect.getInstance().logger.error("GBFS connection failed with status " + status, e);
			canConnect = false;
		}

	}

	@Override
	public boolean isConnected() {
		return canConnect;
	}

	@Override
	public Map<JSONObject, OpenWareDataItem> readMultiple(List<JSONObject> adresses) throws Exception {
		HashMap<JSONObject, OpenWareDataItem> result = new HashMap<JSONObject, OpenWareDataItem>();

		JSONObject res = new JSONObject(req.asJson().getBody().getObject().toString());
		long now = System.currentTimeMillis();
		for (int i = 0; i < res.getJSONObject("data").getJSONArray("bikes").length(); i++) {
			String stationid = res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i)
					.optString("station_id");
			if (stationid.equals("") && !ignoreTripsWithoutStationInfo) {
				double lat = res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getDouble("lat");
				double lon = res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getDouble("lon");
				stationid = virtualStationId(lat, lon);
			}
			res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put("station_id", stationid);
			res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put("lastSeen", now);
			res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put("title",
					res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getString("bike_id"));
			res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).put("description",
					device.optString("name") + ":\n"
							+ DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(now) + "@"
							+ stationid);
		}
		
		
		if(res.getJSONObject("data").getJSONArray("bikes").length()!=0) {
			for (JSONObject item : adresses) {
				switch (item.getString("address")) {
				case "all_locations": {
					OpenWareDataItem candidate = getAllPositions(item, res);
					if (candidate.value().size() > 0)
						result.put(item, candidate);
					break;
				}

				case "changed_locations": {
					if (lastVehicleCount != 0) {
						OpenWareDataItem candidate = getChangedPositions(item, res);
						if (candidate.value().size() > 0)
							result.put(item, candidate);
					}
					break;
				}

				case "trips": {
					if (lastVehicleCount != 0) {
					OpenWareDataItem candidate = getTrips(item, res);
					if (candidate.value().size() > 0)
						result.put(item, candidate);
					}
					break;
				}

				default:
					break;
				}
			}
		}
			
		
		for (int i = 0; i < res.getJSONObject("data").getJSONArray("bikes").length(); i++) {
			vehicles.put(res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i).getString("bike_id"),
					res.getJSONObject("data").getJSONArray("bikes").getJSONObject(i));
		}
		lastVehicleCount = res.getJSONObject("data").getJSONArray("bikes").length();
		return result;
	}

	private OpenWareDataItem getTrips(JSONObject item, JSONObject data) throws Exception {
		JSONArray cVehicles = data.getJSONObject("data").getJSONArray("bikes");
		int TRIP_PRECISION = 5;
		long TRIP_MIN_DURATION = 1000l*60l*5l; //5 Minute Default Min Duration
		
		if(item.getJSONObject("extraOptions").has("tripMinDuration")) {
			TRIP_MIN_DURATION = item.getJSONObject("extraOptions").getLong("tripMinDuration");
		}
		try {
			TRIP_PRECISION = item.getJSONObject("extraOptions").getInt("tripprecision");
		} catch (Exception e) {
			// Nothing to do here
		}
		String itemid = device.getString("objectId") + "::" + item.getString("objectId");
		String source = item.getJSONObject("source").getString("tag");
		ArrayList<OpenWareValue> values = new ArrayList<OpenWareValue>();
		ArrayList<OpenWareValueDimension> dims = new ArrayList<OpenWareValueDimension>();

		for (int i = 0; i < cVehicles.length(); i++) {
			JSONObject cVehicle = cVehicles.getJSONObject(i);
			JSONObject oVehicle = vehicles.get(cVehicle.getString("bike_id"));

			if (oVehicle == null)
				continue;

			String station_start = oVehicle.getString("station_id");
			if (station_start.equals("") && ignoreTripsWithoutStationInfo)
				continue;

			long rentalTime = cVehicle.optLong("lastSeen") - oVehicle.optLong("lastSeen");

			String station_now = cVehicle.getString("station_id");
			if (station_now.equals("") && ignoreTripsWithoutStationInfo)
				continue;

			boolean isSameStation = station_start.substring(0, Math.min(station_start.length(), TRIP_PRECISION))
					.equals(station_now.subSequence(0, Math.min(station_now.length(), TRIP_PRECISION)));
			
			boolean rentalTimetooShort =rentalTime < TRIP_MIN_DURATION; 
			
			if ((rentalTimetooShort || isSameStation)) {
				continue;
			}
			
			OpenWareNumber rentalDuration = new OpenWareNumber(item.optString("label") + " - " + "Fahrzeit", "ms",
					(double) rentalTime);
			OpenWareGeo pos_start = new OpenWareGeo(item.optString("label") + " - " + "Letzte Position", "",
					generateGeoPoint(oVehicle.optDouble("lat"), oVehicle.optDouble("lon"), oVehicle));
			OpenWareGeo pos_end = new OpenWareGeo(item.optString("label") + " - " + "Aktuelle Position", "",
					generateGeoPoint(cVehicle.optDouble("lat"), cVehicle.optDouble("lon"), oVehicle));
			OpenWareString stat_start = new OpenWareString(item.optString("label") + " - " + "Letzte Station", "",
					station_start);
			OpenWareString stat_end = new OpenWareString(item.optString("label") + " - " + "Aktuelle Station", "",
					station_now);
			OpenWareString vehicleid = new OpenWareString(item.optString("label") + " - " + "Fahrzeug", "",
					cVehicle.getString("bike_id"));

			if (dims.size() == 0) {
				dims.add(rentalDuration);
				dims.add(stat_end);
				dims.add(pos_end);
				dims.add(stat_start);
				dims.add(pos_start);
				dims.add(vehicleid);
			}
			OpenWareValue val = new OpenWareValue(cVehicle.optLong("lastSeen") + i);
			val.add(rentalDuration);
			val.add(stat_end);
			val.add(pos_end);
			val.add(stat_start);
			val.add(pos_start);
			val.add(vehicleid);
			values.add(val);
		}
		OpenWareDataItem res = new OpenWareDataItem(itemid, source, device.optString("name"),
				addStyle(item.getJSONObject("extraOptions")), dims);
		if (res.getMeta() == null) {
			res.setMeta(new JSONObject());
		}
		res.value(values);
		return res;
	}

	private OpenWareDataItem getAllPositions(JSONObject item, JSONObject data) {
		JSONObject geoJSON;
		long now = System.currentTimeMillis();
		geoJSON = generateCompleteGeoJSON(data.getJSONObject("data").getJSONArray("bikes"));
		// Live Positions
		OpenWareGeo geo = new OpenWareGeo(item.optString("label") + " - " + "Positionen", "", geoJSON);
		List<OpenWareValueDimension> vTypes = Lists.asList(geo, new OpenWareValueDimension[0]);
		String itemid = device.getString("objectId") + "::" + item.getString("objectId");
		OpenWareDataItem locations = new OpenWareDataItem(itemid, item.getJSONObject("source").getString("tag"),
				device.getString("name"), addStyle(item.getJSONObject("extraOptions")), vTypes);
		OpenWareValue value = new OpenWareValue(now);
		value.add(geo);
		locations.value().add(value);
		return locations;
		//
	}

	private OpenWareDataItem getChangedPositions(JSONObject item, JSONObject data)  {
		JSONObject geoJSON;
		long now = System.currentTimeMillis();
		geoJSON = generateDiffGeoJSON(data.getJSONObject("data").getJSONArray("bikes"));
		// Live Positions
		OpenWareGeo geo = new OpenWareGeo(item.optString("label") + " - " + "Positionen", "", geoJSON);
		List<OpenWareValueDimension> vTypes = Lists.asList(geo, new OpenWareValueDimension[0]);
		String itemid = device.getString("objectId") + "::" + item.getString("objectId");
		OpenWareDataItem locations = new OpenWareDataItem(itemid, item.getJSONObject("source").getString("tag"),
				device.getString("name"), addStyle(item.getJSONObject("extraOptions")), vTypes);
		OpenWareValue value = new OpenWareValue(now);
		value.add(geo);
		locations.value().add(value);
		return locations;
		//
	}

	private JSONObject addStyle(JSONObject meta) {
		if (meta == null) {
			meta = new JSONObject();
		}

		if (!meta.has("marker-symbol"))
			meta.put("marker-symbol", "bicycle");
		if (!meta.has("marker-color"))
			meta.put("marker-color", "#4186c6");
		if (!meta.has("stroke"))
			meta.put("stroke", "#4186c6");
		if (!meta.has("stroke-opacity"))
			meta.put("stroke-opacity", 1);
		if (!meta.has("stroke-width"))
			meta.put("stroke-width", 2);
		if (!meta.has("fill"))
			meta.put("fill", "#FFFFFF");
		if (!meta.has("fill-opacity"))
			meta.put("fill-opacity", 0.5);
		return meta;
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

	private JSONObject generateDiffGeoJSON(JSONArray cVehicels) {
		JSONObject geoJSON = new JSONObject("{ \"type\": \"MultiPoint\",\"coordinates\": []}");
		JSONObject feature = new JSONObject("{\r\n" + "				  \"type\": \"Feature\",\r\n"
				+ "				  \"geometry\": {}\r\n" + "				}");
		JSONArray bikeInfo = new JSONArray();
		for (int i = 0; i < cVehicels.length(); i++) {
			JSONObject cVehicle = cVehicels.getJSONObject(i);
			JSONObject oVehicle = vehicles.get(cVehicle.getString("bike_id"));

			boolean add = false;
			if (oVehicle == null) {
				add = true;
			} else {
				add = (Math.abs(oVehicle.getDouble("lon") - cVehicle.getDouble("lon")) > 0.001
						|| Math.abs(oVehicle.getDouble("lat") - cVehicle.getDouble("lat")) > 0.001);

			}
			if (add) {
				JSONArray coords = new JSONArray();
				coords.put(cVehicle.getDouble("lon"));
				coords.put(cVehicle.getDouble("lat"));
				geoJSON.getJSONArray("coordinates").put(coords);
				bikeInfo.put(cVehicle);
			}

		}
		JSONObject bInfoWrapper = new JSONObject();
		bInfoWrapper.put("data", bikeInfo);
		feature.put("properties", bInfoWrapper);
		feature.put("geometry", geoJSON);
		return feature;
	}

	private JSONObject generateCompleteGeoJSON(JSONArray cVehicels) {
		JSONObject geoJSON = new JSONObject("{ \"type\": \"MultiPoint\",\"coordinates\": []}");
		JSONArray bikeInfo = new JSONArray();
		JSONObject feature = new JSONObject("{\r\n" + "				  \"type\": \"Feature\",\r\n"
				+ "				  \"geometry\": {}\r\n" + "				}");
		for (int i = 0; i < cVehicels.length(); i++) {

			JSONObject cVehicle = cVehicels.getJSONObject(i);
			JSONArray coords = new JSONArray();
			coords.put(cVehicle.getDouble("lon"));
			coords.put(cVehicle.getDouble("lat"));
			geoJSON.getJSONArray("coordinates").put(coords);
			bikeInfo.put(cVehicle);
		}
		feature.put("geometry", geoJSON);
		JSONObject bInfoWrapper = new JSONObject();
		bInfoWrapper.put("data", bikeInfo);
		feature.put("properties", bInfoWrapper);
		return feature;
	}

	private String virtualStationId(double lat, double lon) {
		// 7 Digit precision ~ 75m
		//
		// https://duyanghao.github.io/geohash/
		return Geohash.encode(lat, lon, 12);
	}

	@Override
	public void subscribe(JSONObject address, PLCSubscriptionCallback callback) {
		OpenConnect.getInstance().logger
				.warn("[GBFSCONNECTOR] Subscribe has been triggered, but it is not supported by adapter");

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
		for (int ch; (ch = input.read()) != -1;) {
			sb.append((char) ch);
		}
		return new JSONObject(sb.toString());
	}

}
