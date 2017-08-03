package gov.nist.hla.ii;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GatewayReception extends InterObjReceptionImpl {

	private static final Logger log = LogManager.getLogger(GatewayReception.class);
	
	@Override
	public void receiveInteraction(Double timeStep, String interactionName, Map<String, String> parameters) {
		log.info(String.format("time=%f interaction=%s", timeStep, interactionName));
	}

	@Override
	public void receiveObject(Double timeStep, String objectClassName, String objectName,
			Map<String, String> attributes) {
		log.info(String.format("time=%f object=%s", timeStep, objectClassName));
	}
}
