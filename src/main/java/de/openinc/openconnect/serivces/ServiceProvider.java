package de.openinc.openconnect.serivces;

import java.util.HashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import de.openinc.openconnect.OpenConnect;
import de.openinc.openconnect.pi.SyncServiceInterface;
import de.openinc.openconnect.plcconnector.PLCAdapter;
import de.openinc.openconnect.plcconnector.PluginRoute;

public class ServiceProvider {

	private static HashMap<String, ServiceInterface> serviceMap = new HashMap<String, ServiceInterface>();

	public ServiceProvider() {
		// Get all services and fill serviceMap
		OpenConnect.getInstance().logger.info("Get all services from service loader.");

		ServiceLoader<PLCAdapter> serviceLoaderPLC = ServiceLoader.load(PLCAdapter.class);
		for (PLCAdapter service : serviceLoaderPLC) {
			OpenConnect.getInstance().logger.info("Provided " + service.getAdapterId());
			serviceMap.put(service.getAdapterId(), service);
		}

		ServiceLoader<SyncServiceInterface> serviceLoaderSync = ServiceLoader.load(SyncServiceInterface.class);
		for (SyncServiceInterface service : serviceLoaderSync) {
			OpenConnect.getInstance().logger.info("Provided " + service.getAdapterId());

			try {
				service.getClass().getConstructor().newInstance();
				serviceMap.put(service.getAdapterId(), service);
			} catch (Exception e) {
				OpenConnect.getInstance().logger
						.error("Can not instantiate Adapter: " + service.getClass().getCanonicalName()
								+ ". Make sure to provide an default empty constructor");
				continue;
			}
		}
	}

	/**
	 * Provides all services loaded into the HashMap.
	 * 
	 * @return A HashMap with all services found by
	 *         ServiceLoader.load(PLCAdapter.class).
	 */
	public static HashMap<String, ServiceInterface> getAllServices() {
		return serviceMap;
	}

	/**
	 * Selects the PLCAdapter-implementation for a provided connection string.
	 * 
	 * @param connstr Should be a string containing the adapter/protocol e.g.
	 *                ads.tcp or modbus.tcp or kugelbahn.
	 * @return The PLCAdapter for the provided connection string.
	 */
	public static PLCAdapter getPlcAdapterFromConnectionString(String connstr) {
		String adapterkey = null;
		for (String id : serviceMap.keySet()) {
			if (connstr.toLowerCase().startsWith(id.toLowerCase())) {
				adapterkey = id;
				break;
			}
		}
		if (adapterkey == null)
			return null;
		ServiceInterface service = serviceMap.get(adapterkey);
		if (service == null)
			return null;

		try {
			return (PLCAdapter) service.getClass().getConstructor().newInstance();
		} catch (Exception e) {
			OpenConnect.getInstance().logger.error("Can not instantiate Adapter: "
					+ service.getClass().getCanonicalName() + ". Make sure to provide an default empty constructor");
			return null;
		}
	}

	/**
	 * If you have the adapter id (e.g. ads.tcp or modbus.tcp) this gives you the
	 * associated implementation of PLCAdapter.
	 * 
	 * @param plcid Should be the exact id of the adapter.
	 * @return The PLCAdapter for the provided adapter id.
	 */
	public static PLCAdapter getPlcAdapterFromId(String plcid) {
		ServiceInterface service = serviceMap.get(plcid);
		if (service == null)
			return null;
		try {
			return (PLCAdapter) service.getClass().getConstructor().newInstance();
		} catch (Exception e) {
			OpenConnect.getInstance().logger.error("Can not instantiate Adapter: "
					+ service.getClass().getCanonicalName() + ". Make sure to provide an default empty constructor");
			return null;
		}
	}

	/**
	 * Selects the SyncServiceInterface-implementation for a provided connection
	 * string.
	 * 
	 * @param connstr Should be a string containing the adapter/protocol e.g.
	 *                freshsales.
	 * @return The SyncServiceInterface-Implementation for the provided connection
	 *         string.
	 */
	public static SyncServiceInterface getSyncServiceFromConnectionString(String connstr) {
		String adapterkey = null;
		for (String id : serviceMap.keySet()) {
			if (connstr.toLowerCase().startsWith(id.toLowerCase())) {
				adapterkey = id;
				break;
			}
		}
		if (adapterkey == null)
			return null;
		ServiceInterface service = serviceMap.get(adapterkey);
		if (service == null)
			return null;
		try {
			return (SyncServiceInterface) service.getClass().getConstructor().newInstance();
		} catch (Exception e) {
			OpenConnect.getInstance().logger.error("Can not instantiate Adapter: "
					+ service.getClass().getCanonicalName() + ". Make sure to provide an default empty constructor");
			return null;
		}
	}

	/**
	 * If you have the adapter id (e.g. ads.tcp or modbus.tcp) this gives you the
	 * associated implementation of SyncServiceInterface.
	 * 
	 * @param id Should be the exact id of the adapter.
	 * @return The SyncServiceInterface for the provided adapter id.
	 */
	public static SyncServiceInterface getSyncServiceFromId(String id) {
		ServiceInterface service = serviceMap.get(id);
		if (service == null)
			return null;
		try {
			return (SyncServiceInterface) service.getClass().getConstructor().newInstance();
		} catch (Exception e) {
			OpenConnect.getInstance().logger.error("Can not instantiate Adapter: "
					+ service.getClass().getCanonicalName() + ". Make sure to provide a default empty constructor");
			return null;
		}
	}

	/**
	 * Provides all SyncServiceInterface-Implementations which implements a route.
	 * Use this for Java below version 16.
	 * 
	 * @return A List of ServiceInterfaces.
	 */
	public static List<ServiceInterface> getAllRouteImplementations() {
		return serviceMap.values().stream().map((service) -> {
			if (service instanceof PluginRoute) {
				return service;
			}
			return null;
		}).filter((service) -> {
			return service != null;
		}).collect(Collectors.toList());
	}

	/**
	 * Provides all SyncServiceInterface-Implementations which implements a route.
	 * Use this for Java higher than version 16.
	 * 
	 * @return A List of ServiceInterfaces.
	 */
//	public static List<ServiceInterface> getAllRouteImplementations() {
//		return serviceMap.values().stream().map((service) -> {
//			if (service instanceof PluginRoute && service instanceof SyncServiceInterface) {
//				return service;
//			}
//			return null;
//		}).filter((service) -> {
//			return service != null;
//		}).toList();
//	}
}
