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
        publications.add(new HLAPacket(instanceHandle, attributes, HLAPacket.TYPE.OBJECT));
    }
    
    protected void addObject(String instanceName, Map<String, String> attributes) {
        publications.add(new HLAPacket(instanceName, attributes, HLAPacket.TYPE.OBJECT));
    }

    protected void addInteraction(String interactionClass, Map<String, String> parameters) {
        publications.add(new HLAPacket(interactionClass, parameters, HLAPacket.TYPE.INTERACTION));
    }

    @Override
    public Queue<HLAPacket> getPublications(Double logicalTime) {
        return publications;
    }
    
    @Override
    public void receiveInteraction(Double timeStep, String interactionClass, Map<String, String> parameters) {}

    @Override
    public void receiveObject(Double timeStep, String objectClass, String objectName, Map<String, String> attributes) {}
    
    @Override
    public void initializeSelf() {}

    @Override
    public void initializeWithPeers() {}

    @Override
    public void beforeTimeStep(Double timeStep) {}

    @Override
    public void afterTimeStep(Double timeStep) {}
}
