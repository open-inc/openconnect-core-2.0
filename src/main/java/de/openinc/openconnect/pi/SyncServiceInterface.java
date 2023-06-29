package de.openinc.openconnect.pi;

import java.util.Map;

import de.openinc.openconnect.serivces.ServiceInterface;

public interface SyncServiceInterface extends ServiceInterface {

	public boolean init(Map<String, Object> options);

	public String updateRessource(String classOfRessource, String id);

	public String getType();
}
