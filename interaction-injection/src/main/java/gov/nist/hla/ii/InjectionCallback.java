package gov.nist.hla.ii;

import java.util.Map;
import java.util.Queue;

public interface InjectionCallback {
    Queue<HLAPacket> getPublications(Double logicalTime);
    
    void receiveInteraction(Double timeStep, String className, Map<String, String> parameters);

    void receiveObject(Double timeStep, String className, String instanceName, Map<String, String> attributes);
    
    void initializeSelf();

    void initializeWithPeers();

    void beforeTimeStep(Double timeStep);

    void afterTimeStep(Double timeStep);
    
    void afterResignation();
}
