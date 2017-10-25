package gov.nist.hla.ii;

import java.util.Map;
import java.util.Queue;

public interface InjectionCallback {

    Queue<HLAPacket> getPreSynchPublications();

    Queue<HLAPacket> getPublications(Double logicalTime);

    void addObject(String objectClass, Map<String, String> attributes);

    void addInteraction(String interactionClass, Map<String, String> parameters);
}
