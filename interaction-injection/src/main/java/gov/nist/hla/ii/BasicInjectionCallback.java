package gov.nist.hla.ii;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BasicInjectionCallback implements InjectionCallback {
    private static final Logger log = LogManager.getLogger();
    
    @Override
    public void receiveInteraction(Double timeStep, String className, Map<String, String> parameters) {
        log.trace("default receiveInteraction callback");
    }

    @Override
    public void receiveObject(Double timeStep, String className, String instanceName, Map<String, String> attributes) {
        log.trace("default receiveObject callback");
    }
    
    @Override
    public void initializeSelf() {
        log.trace("default initializeSelf callback");
    }

    @Override
    public void initializeWithPeers() {
        log.trace("default initializeWithPeers callback");
    }

    @Override
    public void beforeTimeStep(Double timeStep) {
        log.trace("default beforeTimeStep callback");
    }

    @Override
    public void afterTimeStep(Double timeStep) {
        log.trace("default afterTimeStep callback");
    }
    
    @Override
    public void afterResignation() {
        log.trace("default afterResignation callback");
    }
}
