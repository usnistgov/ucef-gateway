package gov.nist.hla.ii;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
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
        private int objectHandle;
        private int objectClass;
        private String objectName;

        public ObjectDetails(int objectHandle, int objectClass, String objectName) {
            this.objectHandle = objectHandle;
            this.objectClass = objectClass;
            this.objectName = objectName;
        }

        public int getObjectHandle() {
            return objectHandle;
        }

        public int getObjectClass() {
            return objectClass;
        }

        public String getObjectName() {
            return objectName;
        }
    }

    // synchronization point labels that have been announced but not achieved
    private Set<String> pendingSynchronizationPoints = new HashSet<String>();

    // map the handle for a discovered object instance to its associated ObjectDetails
    private Map<Integer, ObjectDetails> objectInstances = new HashMap<Integer, ObjectDetails>();

    // names of previously discovered object instances that have since been removed
    private LinkedList<String> removedObjectNames = new LinkedList<String>();

    private LinkedList<Interaction> receivedInteractions = new LinkedList<Interaction>();
    private LinkedList<ObjectReflection> receivedObjectReflections = new LinkedList<ObjectReflection>();

    private boolean isTimeAdvancing = false;
    private boolean isTimeRegulating = false;
    private boolean isTimeConstrained = false;

    private double logicalTime = 0D;

    public void announceSynchronizationPoint(String synchronizationPointLabel, byte[] userSuppliedTag)
            throws FederateInternalError {
        if (pendingSynchronizationPoints.contains(synchronizationPointLabel)) {
            log.warn("duplicate announcement of synchronization point: " + synchronizationPointLabel);
        } else {
            pendingSynchronizationPoints.add(synchronizationPointLabel);
            log.info("synchronization point announced: " + synchronizationPointLabel);
        }
    }

    public void federationSynchronized(String synchronizationPointLabel) throws FederateInternalError {
        pendingSynchronizationPoints.remove(synchronizationPointLabel);
        log.info("synchronization point achieved: " + synchronizationPointLabel);
    }

    public void timeRegulationEnabled(LogicalTime theFederateTime)
            throws InvalidFederationTime, EnableTimeRegulationWasNotPending, FederateInternalError {
        isTimeRegulating = true;
        logicalTime = convertTime(theFederateTime);
        log.info("time regulation enabled: t=" + logicalTime);
    }

    public void timeConstrainedEnabled(LogicalTime theFederateTime)
            throws InvalidFederationTime, EnableTimeConstrainedWasNotPending, FederateInternalError {
        isTimeConstrained = true;
        logicalTime = convertTime(theFederateTime);
        log.info("time constrained enabled: t=" + logicalTime);
    }

    public void timeAdvanceGrant(LogicalTime theTime)
            throws InvalidFederationTime, TimeAdvanceWasNotInProgress, FederateInternalError {
        isTimeAdvancing = false;
        logicalTime = convertTime(theTime);
        log.debug("time advance granted: t=" + logicalTime);
    }

    public void receiveInteraction(int interactionClass, ReceivedInteraction theInteraction, byte[] userSuppliedTag)
            throws InteractionClassNotKnown, InteractionParameterNotKnown, FederateInternalError {
        try {
            receiveInteraction(interactionClass, theInteraction, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    public void receiveInteraction(int interactionClass, ReceivedInteraction theInteraction, byte[] userSuppliedTag,
            LogicalTime theTime, EventRetractionHandle eventRetractionHandle)
            throws InteractionClassNotKnown, InteractionParameterNotKnown, InvalidFederationTime,
            FederateInternalError {
        Interaction newInteraction = new Interaction(interactionClass, theInteraction);
        receivedInteractions.add(newInteraction);
        log.debug("received " + newInteraction.toString());
    }

    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName)
            throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        if (objectInstances.put(theObject, new ObjectDetails(theObject, theObjectClass, objectName)) != null) {
            throw new FederateInternalError("discovered multiple object instances with handle " + theObject);
        }
        log.info("discovered object=" + objectName + " handle="+ theObject + "class=" + theObjectClass);
    }

    public void reflectAttributeValues(int theObject, ReflectedAttributes theAttributes, byte[] userSuppliedTag)
            throws ObjectNotKnown, AttributeNotKnown, FederateOwnsAttributes, FederateInternalError {
        try {
            reflectAttributeValues(theObject, theAttributes, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    public void reflectAttributeValues(int theObject, ReflectedAttributes theAttributes, byte[] userSuppliedTag,
            LogicalTime theTime, EventRetractionHandle retractionHandle)
            throws ObjectNotKnown, AttributeNotKnown, FederateOwnsAttributes, InvalidFederationTime,
            FederateInternalError {
        ObjectDetails details = objectInstances.get(theObject);
        if (details == null) {
            throw new ObjectNotKnown("no discovered object instance with handle " + theObject);
        }
        int theObjectClass = details.getObjectClass();
        String objectName = details.getObjectName();
        ObjectReflection newObjectReflection = new ObjectReflection(theObjectClass, objectName, theAttributes);
        receivedObjectReflections.add(newObjectReflection);
        log.debug("received " + newObjectReflection.toString());
    }

    public void removeObjectInstance(int theObject, byte[] userSuppliedTag)
            throws ObjectNotKnown, FederateInternalError {
        try {
            removeObjectInstance(theObject, userSuppliedTag, null, null);
        } catch (InvalidFederationTime e) {
            throw new FederateInternalError(e);
        }
    }

    public void removeObjectInstance(int theObject, byte[] userSuppliedTag, LogicalTime theTime,
            EventRetractionHandle retractionHandle)
            throws ObjectNotKnown, InvalidFederationTime, FederateInternalError {
        ObjectDetails details = objectInstances.remove(theObject);
        if (details == null) {
            throw new ObjectNotKnown("no discovered object instance with handle " + theObject);
        }
        String objectName = details.getObjectName();
        removedObjectNames.add(objectName);
        log.info("received removal notice for object=" + objectName + " handle=" + theObject);
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
        return receivedInteractions.pollFirst(); // destructive read
    }

    public ObjectReflection nextObjectReflection() {
        return receivedObjectReflections.pollFirst(); // destructive read
    }

    public String nextRemovedObjectName() {
        return removedObjectNames.pollFirst(); // destructive read
    }

    private double convertTime(LogicalTime logicalTime) {
        // conversion from Portico to java types
        return ((DoubleTime) logicalTime).getTime();
    }
}
