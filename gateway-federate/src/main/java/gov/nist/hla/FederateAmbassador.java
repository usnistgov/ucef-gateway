package gov.nist.hla;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.portico.impl.hla13.types.DoubleTime;

import hla.rti.AttributeNotKnown;
import hla.rti.CouldNotDiscover;
import hla.rti.EnableTimeConstrainedWasNotPending;
import hla.rti.EnableTimeRegulationWasNotPending;
import hla.rti.EventRetractionHandle;
import hla.rti.FederateInternalError;
import hla.rti.FederateOwnsAttributes;
import hla.rti.InteractionClassNotKnown;
import hla.rti.InteractionParameterNotKnown;
import hla.rti.InvalidFederationTime;
import hla.rti.LogicalTime;
import hla.rti.ObjectClassNotKnown;
import hla.rti.ObjectNotKnown;
import hla.rti.ReceivedInteraction;
import hla.rti.ReflectedAttributes;
import hla.rti.TimeAdvanceWasNotInProgress;
import hla.rti.jlc.NullFederateAmbassador;

// assume single threaded environment
public class FederateAmbassador extends NullFederateAmbassador {
    private static final Logger log = LogManager.getLogger();

    private class ObjectDetails {
        private int instanceHandle;
        private int classHandle;
        private String instanceName;

        public ObjectDetails(int instanceHandle, int classHandle, String instanceName) {
            this.instanceHandle = instanceHandle;
            this.classHandle = classHandle;
            this.instanceName = instanceName;
        }

        public int getInstanceHandle() {
            return instanceHandle;
        }

        public int getClassHandle() {
            return classHandle;
        }

        public String getInstanceName() {
            return instanceName;
        }

        public String toString() {
            return String.format("instance=%d class=%d name=%s", instanceHandle, classHandle, instanceName);
        }
    }

    // set of synchronization points that have been announced but not achieved
    private Set<String> pendingSynchronizationPoints = new HashSet<String>();

    // map the instance handle of a discovered object to its associated ObjectDetails
    private Map<Integer, ObjectDetails> objectInstances = new HashMap<Integer, ObjectDetails>();

    // names of discovered object instances that have not been processed
    private Queue<String> discoveredObjectInstances = new LinkedList<String>();

    // names of previously discovered object instances that have since been removed
    private Queue<String> removedObjectInstances = new LinkedList<String>();

    private Queue<Interaction> receivedInteractions = new LinkedList<Interaction>();
    private Queue<ObjectReflection> receivedObjectReflections = new LinkedList<ObjectReflection>();

    private boolean isTimeAdvancing = false;
    private boolean isTimeRegulating = false;
    private boolean isTimeConstrained = false;

    private double logicalTime = 0.0;

    @Override
    public void announceSynchronizationPoint(String synchronizationPointLabel, byte[] userSuppliedTag)
            throws FederateInternalError {
        if (pendingSynchronizationPoints.contains(synchronizationPointLabel)) {
            log.warn("duplicate announcement of synchronization point: " + synchronizationPointLabel);
        } else {
            pendingSynchronizationPoints.add(synchronizationPointLabel);
            log.info("synchronization point announced: " + synchronizationPointLabel);
        }
    }

    @Override
    public void federationSynchronized(String synchronizationPointLabel) throws FederateInternalError {
        pendingSynchronizationPoints.remove(synchronizationPointLabel);
        log.info("synchronization point achieved: " + synchronizationPointLabel);
    }

    @Override
    public void timeRegulationEnabled(LogicalTime theFederateTime)
            throws InvalidFederationTime, EnableTimeRegulationWasNotPending, FederateInternalError {
        isTimeRegulating = true;
        logicalTime = convertTime(theFederateTime);
        log.info("time regulation enabled: t=" + logicalTime);
    }

    @Override
    public void timeConstrainedEnabled(LogicalTime theFederateTime)
            throws InvalidFederationTime, EnableTimeConstrainedWasNotPending, FederateInternalError {
        isTimeConstrained = true;
        logicalTime = convertTime(theFederateTime);
        log.info("time constrained enabled: t=" + logicalTime);
    }

    @Override
    public void timeAdvanceGrant(LogicalTime theTime)
            throws InvalidFederationTime, TimeAdvanceWasNotInProgress, FederateInternalError {
        isTimeAdvancing = false;
        logicalTime = convertTime(theTime);
        log.info("time advance granted: t=" + logicalTime);
    }

    @Override
    public void receiveInteraction(int interactionClass, ReceivedInteraction theInteraction, byte[] userSuppliedTag)
            throws InteractionClassNotKnown, InteractionParameterNotKnown, FederateInternalError {
        try {
            receiveInteraction(interactionClass, theInteraction, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    @Override
    public void receiveInteraction(int interactionClass, ReceivedInteraction theInteraction, byte[] userSuppliedTag,
            LogicalTime theTime, EventRetractionHandle eventRetractionHandle)
                    throws InteractionClassNotKnown, InteractionParameterNotKnown, InvalidFederationTime,
                    FederateInternalError {
        Interaction newInteraction = new Interaction(interactionClass, theInteraction);
        receivedInteractions.add(newInteraction);
        log.debug("received " + newInteraction.toString());
    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName)
            throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        ObjectDetails details = new ObjectDetails(theObject, theObjectClass, objectName);
        if (objectInstances.put(theObject, details) != null) {
            throw new FederateInternalError("discovered duplicate object " + details.toString());
        }
        discoveredObjectInstances.add(details.getInstanceName());
        log.info("discovered object " + details.toString());
    }

    @Override
    public void reflectAttributeValues(int theObject, ReflectedAttributes theAttributes, byte[] userSuppliedTag)
            throws ObjectNotKnown, AttributeNotKnown, FederateOwnsAttributes, FederateInternalError {
        try {
            reflectAttributeValues(theObject, theAttributes, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    @Override
    public void reflectAttributeValues(int theObject, ReflectedAttributes theAttributes, byte[] userSuppliedTag,
            LogicalTime theTime, EventRetractionHandle retractionHandle)
                    throws ObjectNotKnown, AttributeNotKnown, FederateOwnsAttributes, InvalidFederationTime,
                    FederateInternalError {
        ObjectDetails details = objectInstances.get(theObject);
        if (details == null) {
            throw new ObjectNotKnown("no discovered object instance with handle " + theObject);
        }
        ObjectReflection newObjectReflection = 
                new ObjectReflection(details.getClassHandle(), details.getInstanceName(), theAttributes);
        receivedObjectReflections.add(newObjectReflection);
        log.debug("received " + newObjectReflection.toString());
    }

    @Override
    public void removeObjectInstance(int theObject, byte[] userSuppliedTag)
            throws ObjectNotKnown, FederateInternalError {
        try {
            removeObjectInstance(theObject, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    @Override
    public void removeObjectInstance(int theObject, byte[] userSuppliedTag, LogicalTime theTime,
            EventRetractionHandle retractionHandle)
                    throws ObjectNotKnown, InvalidFederationTime, FederateInternalError {
        ObjectDetails details = objectInstances.remove(theObject);
        if (details == null) {
            throw new ObjectNotKnown("no discovered object instance with handle " + theObject);
        }
        // it is possible this instance is still in the discoveredObjectInstances queue
        removedObjectInstances.add(details.getInstanceName());
        log.info("removed object " + details.toString());
    }

    public boolean isSynchronizationPointPending(String label) {
        return pendingSynchronizationPoints.contains(label);
    }

    public double getLogicalTime() {
        return logicalTime;
    }

    public void setTimeAdvancing() {
        this.isTimeAdvancing = true;
    }

    public boolean isTimeAdvancing() {
        return isTimeAdvancing;
    }

    public boolean isTimeRegulating() {
        return isTimeRegulating;
    }

    public boolean isTimeConstrained() {
        return isTimeConstrained;
    }

    public Interaction nextInteraction() {
        return receivedInteractions.poll(); // destructive read
    }

    public ObjectReflection nextObjectReflection() {
        return receivedObjectReflections.poll(); // destructive read
    }

    public String nextDiscoveredObjectName() {
        return discoveredObjectInstances.poll(); // destructive read
    }

    public String nextRemovedObjectName() {
        return removedObjectInstances.poll(); // destructive read
    }

    private double convertTime(LogicalTime logicalTime) {
        // conversion from Portico to java types
        return ((DoubleTime) logicalTime).getTime();
    }
}
