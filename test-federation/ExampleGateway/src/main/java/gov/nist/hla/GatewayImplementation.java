package gov.nist.hla;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nist.hla.gateway.GatewayCallback;
import gov.nist.hla.gateway.GatewayFederate;
import gov.nist.hla.gateway.GatewayFederateConfig;
import hla.rti.AttributeNotOwned;
import hla.rti.FederateNotExecutionMember;
import hla.rti.InteractionClassNotPublished;
import hla.rti.InvalidFederationTime;
import hla.rti.NameNotFound;
import hla.rti.ObjectAlreadyRegistered;
import hla.rti.ObjectClassNotPublished;
import hla.rti.ObjectNotKnown;

public class GatewayImplementation implements GatewayCallback {
    private static final Logger log = LogManager.getLogger();

    private static final String TEST_INTERACTION = "InteractionRoot.C2WInteractionRoot.TestInteraction";

    private GatewayFederate gateway;

    private Map<String, String> objectState = new HashMap<String, String>();

    public static void main(String[] args)
            throws IOException {
        if (args.length != 1) {
            log.error("missing command line argument for JSON configuration file");
            return;
        }

        GatewayFederateConfig config = GatewayFederate.readConfiguration(args[0]);
        GatewayImplementation gatewayFederate = new GatewayImplementation(config);
        gatewayFederate.run();
    }

    public GatewayImplementation(GatewayFederateConfig configuration) {
        this.gateway = new GatewayFederate(configuration, this);
    }

    public void run() {
        log.trace("run");
        gateway.run();
    }

    public void initializeSelf() {
        log.trace("initializeSelf");
    }

    public void initializeWithPeers() {
        log.trace("initializeWithPeers");
        try {
            gateway.registerObjectInstance("ObjectRoot.TestObject", "GatewayObject");
        } catch (FederateNotExecutionMember | NameNotFound | ObjectClassNotPublished | ObjectAlreadyRegistered e) {
            throw new RuntimeException("failed to register object", e);
        }
    }

    public void receiveInteraction(Double timeStep, String className, Map<String, String> parameters) {
        log.trace(String.format("receiveInteraction %f %s %s", timeStep, className, parameters.toString()));

        if (className.equals("InteractionRoot.C2WInteractionRoot.SimulationControl.SimEnd")) {
            return;
        }

        Map<String, String> interactionValues = new HashMap<String, String>();
        interactionValues.put("sequenceNumber", parameters.get("sequenceNumber"));
        interactionValues.put("booleanValue", parameters.get("booleanValue"));
        interactionValues.put("doubleValue", parameters.get("doubleValue"));
        interactionValues.put("intValue", parameters.get("intValue"));
        interactionValues.put("stringValue", parameters.get("stringValue"));

        try {
            gateway.sendInteraction(TEST_INTERACTION, interactionValues, gateway.getTimeStamp());
            log.info(String.format("t=%f sent %s using %s", timeStep, TEST_INTERACTION, interactionValues.toString()));
        } catch (FederateNotExecutionMember | NameNotFound | InteractionClassNotPublished | InvalidFederationTime e) {
            throw new RuntimeException("failed to send interaction", e);
        }
    }

    public void receiveObject(Double timeStep, String className, String instanceName, Map<String, String> attributes) {
        log.trace(String.format("receiveObject %f %s %s %s", timeStep, className, instanceName, attributes.toString()));

        if (className.startsWith("ObjectRoot.Manager.")) {
            // to demonstrate how to receive ObjectRoot.Manager.Federate (and other RTI managed objects)
            log.info("received RTI managed object {} ({}): {}", instanceName, className, attributes);
            return;
        }

        objectState.putAll(attributes); // attributes will not contain entries for unchanged values
        log.info("received updated object values " + attributes.toString());

        try {
            gateway.updateObject("GatewayObject", objectState, gateway.getTimeStamp());
            log.info(String.format("t=%f sent %s using %s", timeStep, "GatewayObject", objectState.toString()));
        } catch (FederateNotExecutionMember | ObjectNotKnown | NameNotFound | AttributeNotOwned
                | InvalidFederationTime e) {
            throw new RuntimeException("failed to update object", e);
        }
    }

    public void doTimeStep(Double timeStep) {
        log.trace("doTimeStep " + timeStep);
    }
    
    public void prepareToResign() {
        log.trace("prepareToResign");
    }

    public void terminate() {
        log.trace("terminate");
    }
}
