package gov.nist.hla.ii;

import java.util.Map;

import hla.rti.RTIambassador;

public interface ReceptionCallback {

    void receiveInteraction(Double timeStep, String interactionClass, Map<String, String> parameters);

    void receiveObject(Double timeStep, String objectClass, String objectName, Map<String, String> attributes);
}
