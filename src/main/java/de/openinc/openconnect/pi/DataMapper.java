package de.openinc.openconnect.pi;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.DateTool;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.openinc.openconnect.OpenConnect;

public class DataMapper {
//	private String JSONCONFIGFILE;
	private JSONObject config;
	public Map<String, ContextMethod> contextMethods;
//	private static DataMapper me;

//	public static DataMapper getInstance() throws IOException {
//		if (me == null) {
//			me = new DataMapper();
//		}
//		return me;
//	}

	public DataMapper(String mappingsFilePath, Map<String, ContextMethod> contextMethods) throws IOException {
		Velocity.init();

		String configString = Files.readAllLines(Path.of(mappingsFilePath), Charset.forName("UTF-8")).stream()
				.collect(Collectors.joining());
		this.contextMethods = contextMethods;
		this.config = new JSONObject(configString);
	}

	public boolean isNull(Object o) {
		if (o != null && String.valueOf(o) != "null") {
			return false;
		}
		return true;
	}

	public String parseString(String value) {
		if (!this.isNull(value) && value.length() != 0) {
			return value;
		} else {
			return "0";
		}
	}

	public JSONObject map(String from, String to, String className, JSONObject context) {
		try {
			JSONObject targetConf = config.getJSONObject(from).getJSONObject(to).getJSONObject(className);
			VelocityContext ctx = new VelocityContext();
			ctx.put("this", this);
			ctx.put("contextMethods", this.contextMethods);
			ctx.put("date", new DateTool());
			ctx.put("source", context);
			ctx.put("target", targetConf);
			JSONObject result = new JSONObject();
			for (String key : targetConf.keySet()) {
				OpenConnect.getInstance().logger.info("Mapping with key " + key);
				try {
					JSONObject keyConf = targetConf.getJSONObject(key);
					String type = keyConf.optString("type", "String");
					String res = evaluate(keyConf.getString("value"), ctx);
					switch (type.toLowerCase()) {
					case "string":
						result.put(key, res);
						break;
					case "boolean":
						result.put(key, Boolean.valueOf(res));
						break;
					case "number":
						result.put(key, Double.valueOf(res));
						break;
					case "object":
						result.put(key, new JSONObject(res));
						break;
					case "array":
						result.put(key, new JSONArray(res));
						break;
					default:
						result.put(key, res);
						break;
					}
				} catch (JSONException e) {
					OpenConnect.getInstance().logger.warn("Exception in mapping. Continue.");
					OpenConnect.getInstance().logger.info(e.getMessage());
					result.put(key, context.get(targetConf.getString(key)).toString());
				}
			}
			OpenConnect.getInstance().logger.info(result.toString(2));
			return result;
		} catch (Exception e) {
			OpenConnect.getInstance().logger.warn("Exception in mapping. Throwing exception.");
			OpenConnect.getInstance().logger.info(e.getMessage());
			throw new RuntimeException("Could not map body contents.");
		}
	}

	private String evaluate(String template, VelocityContext context) {
		StringWriter sw = new StringWriter();
		Reader r = new StringReader(template);
		Velocity.evaluate(context, sw, "Velocity", r);
		return sw.toString();
	}

}
