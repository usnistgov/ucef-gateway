package gov.nist.hla.ii;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BasicInjectionCallback implements InjectionCallback {
    private static final Logger log = LogManager.getLogger();
    
    protected Queue<HLAPacket> publications = new ConcurrentLinkedQueue<HLAPacket>();
    
    protected void addObject(int instanceHandle, Map<String, String> attributes) {
        HLAPacket newPublication = new HLAPacket(instanceHandle, attributes, HLAPacket.TYPE.OBJECT);
        publications.add(newPublication);
        log.debug("added publication for " + newPublication.toString());
    }
    
    protected void addObject(String instanceName, Map<String, String> attributes) {
        HLAPacket newPublication = new HLAPacket(instanceName, attributes, HLAPacket.TYPE.OBJECT);
        publications.add(newPublication);
        log.debug("added publication for " + newPublication.toString());
    }

    protected void addInteraction(String className, Map<String, String> parameters) {
        HLAPacket newPublication = new HLAPacket(className, parameters, HLAPacket.TYPE.INTERACTION);
        publications.add(newPublication);
        log.debug("added publication for " + newPublication.toString());
    }

    @Override
    public Queue<HLAPacket> getPublications(Double logicalTime) {
        log.trace("default getPublications callback");
        return publications;
    }
    
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
