package de.openinc.openconnect.pi.requesthandling;

import org.json.JSONArray;

import de.openinc.openconnect.pi.ServiceEndpointInterface;

public interface RequestHandlingInterface {

	public ServiceEndpointInterface fromService();

	public ServiceEndpointInterface toService();

	public String typeRequest();

	public String httpMethod();

	public void processRequest(JSONArray bodycontent) throws Exception;

}
