package de.openinc.openconnect.pi.requesthandling;

import java.util.HashMap;
import java.util.ServiceLoader;

import de.openinc.openconnect.OpenConnect;
import io.javalin.http.BadRequestResponse;

public class RequestHandlingFactory {

	private HashMap<String, RequestHandlingInterface> casesMap = new HashMap<String, RequestHandlingInterface>();

	public RequestHandlingFactory() {
		ServiceLoader<RequestHandlingInterface> casesLoader = ServiceLoader.load(RequestHandlingInterface.class);
		for (RequestHandlingInterface caseImpl : casesLoader) {
			OpenConnect.getInstance().logger.info("Provided implementation: " + caseImpl.fromService().getName()
					.toLowerCase().concat("-").concat(caseImpl.toService().getName()).concat("-")
					.concat(caseImpl.typeRequest()).concat("-").concat(caseImpl.httpMethod()).toLowerCase());
			casesMap.put(caseImpl.fromService().getName().toLowerCase().concat("-")
					.concat(caseImpl.toService().getName()).concat("-").concat(caseImpl.typeRequest()).concat("-")
					.concat(caseImpl.httpMethod()).toLowerCase(), caseImpl);
		}
	}

	public HashMap<String, RequestHandlingInterface> getAllRequestCases() {
		return casesMap;
	}

	public RequestHandlingInterface findRequestImplementation(String fromparam, String toparam, String typeparam,
			String method) {
		OpenConnect.getInstance().logger.info("Look for implementation: " + fromparam.concat("-").concat(toparam)
				.concat("-").concat(typeparam).concat("-").concat(method).toLowerCase());

		RequestHandlingInterface caseImpl = casesMap.get(fromparam.concat("-").concat(toparam).concat("-")
				.concat(typeparam).concat("-").concat(method).toLowerCase());
		if (caseImpl == null) {
			throw new BadRequestResponse("Could not handle request due to missing implementation.");
		}
		return caseImpl;
	}

}
