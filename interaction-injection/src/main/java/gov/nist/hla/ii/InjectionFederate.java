package gov.nist.hla.ii;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cpswt.hla.SynchronizationPoints;
import org.ieee.standards.ieee1516._2010.AttributeType;
import org.ieee.standards.ieee1516._2010.InteractionClassType;
import org.ieee.standards.ieee1516._2010.ObjectClassType;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.hla.ii.config.InjectionFederateConfig;
import gov.nist.hla.ii.exception.RTIAmbassadorException;
import hla.rti.AsynchronousDeliveryAlreadyEnabled;
import hla.rti.AttributeHandleSet;
import hla.rti.AttributeNotDefined;
import hla.rti.AttributeNotOwned;
import hla.rti.ConcurrentAccessAttempted;
import hla.rti.EnableTimeConstrainedPending;
import hla.rti.EnableTimeRegulationPending;
import hla.rti.FederateAlreadyExecutionMember;
import hla.rti.FederateLoggingServiceCalls;
import hla.rti.FederateNotExecutionMember;
import hla.rti.FederationExecutionDoesNotExist;
import hla.rti.InteractionClassNotDefined;
import hla.rti.InteractionClassNotPublished;
import hla.rti.InteractionParameterNotDefined;
import hla.rti.InvalidFederationTime;
import hla.rti.NameNotFound;
import hla.rti.ObjectAlreadyRegistered;
import hla.rti.ObjectClassNotDefined;
import hla.rti.ObjectClassNotPublished;
import hla.rti.ObjectNotKnown;
import hla.rti.OwnershipAcquisitionPending;
import hla.rti.RTIambassador;
import hla.rti.RTIexception;
import hla.rti.RTIinternalError;
import hla.rti.ResignAction;
import hla.rti.RestoreInProgress;
import hla.rti.SaveInProgress;
import hla.rti.SuppliedAttributes;
import hla.rti.SuppliedParameters;
import hla.rti.TimeConstrainedAlreadyEnabled;
import hla.rti.TimeRegulationAlreadyEnabled;
import hla.rti.jlc.RtiFactoryFactory;

// no schema validation of name fields
// we cannot inject future messages (at specific time)
// no dynamic publish/subscribe capability
// no discover object instances
public class InjectionFederate implements Runnable {
    private static final Logger log = LogManager.getLogger();

    private static final String SIMULATION_END = "InteractionRoot.C2WInteractionRoot.SimulationControl.SimEnd";
    private static final String FEDERATE_JOIN = "InteractionRoot.C2WInteractionRoot.FederateJoinInteraction";
    
    private InjectionFederateConfig configuration;
    private InjectionCallback injectionCallback;
    
    private RTIambassador rtiAmb;
    private FederateAmbassador fedAmb;
    private ObjectModel objectModel;
    
    private boolean receivedSimEnd = false;
    private boolean exitFlag = false;
    
    public static InjectionFederateConfig readConfiguration(String filepath)
            throws IOException {
        log.debug("reading JSON configuration file at " + filepath);
        File configFile = Paths.get(filepath).toFile();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFile, InjectionFederateConfig.class);
    }
    
    public InjectionFederate(InjectionFederateConfig configuration, InjectionCallback injectionCallback) {
        this.configuration = configuration;
        this.injectionCallback = injectionCallback;
        try {
            rtiAmb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        fedAmb = new FederateAmbassador();
        objectModel = new ObjectModel(configuration.getFomFilepath());
    }
    
    public void run() {
        log.trace("run");

        try {
            joinFederationExecution();
        } catch (InterruptedException e) {
            log.fatal("run halted due to interrupt");
            return;
        }
        
        enableAsynchronousDelivery();
        enableTimeConstrained();
        enableTimeRegulation();
        
        publishAndSubscribe();
        notifyOfFederationJoin();
        
        injectionCallback.initializeSelf();
        synchronize(SynchronizationPoints.ReadyToPopulate);
        injectionCallback.initializeWithPeers();
        synchronize(SynchronizationPoints.ReadyToRun);
        
        while (!isExitCondition()) {
            log.trace("run t=" + getLogicalTime());
            injectionCallback.beforeTimeStep(getLogicalTime());
            handleSubscriptions();
            sendPublications();
            injectionCallback.afterTimeStep(getLogicalTime());
            advanceLogicalTime();
        }
        
        if (receivedSimEnd) {
            synchronize(SynchronizationPoints.ReadyToResign);
        }
        resignFederationExecution();
        injectionCallback.afterResignation();
    }
    
    public void synchronize(String label) {
        log.info("waiting for announcement of the synchronization point " + label);
        while (!fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }

        try {
            rtiAmb.synchronizationPointAchieved(label);
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }

        log.info("waiting for federation to synchronize on synchronization point " + label);
        while (fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }
        log.info("federation synchronized on " + label);
    }
    
    public void tick() {
        log.trace("tick");
        try {
            rtiAmb.tick();
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        handleSubscriptions();
    }
    
    public ObjectModel getObjectModel() {
        return objectModel;
    }
    
    public double getLogicalTime() {
        return fedAmb.getLogicalTime();
    }
    
    // it will take 1 iteration to exit
    public void requestExit() {
        log.info("application requested exit");
        this.exitFlag = true;
    }
    
    public String registerObjectInstance(String className)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished {
        log.trace("registerObjectInstance " + className);
        
        int classHandle;
        int instanceHandle;
        String instanceName;
        
        try {
            classHandle = rtiAmb.getObjectClassHandle(className);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            instanceHandle = rtiAmb.registerObjectInstance(classHandle);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in registerObjectInstance");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.error("save and restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            instanceName = rtiAmb.getObjectInstanceName(instanceHandle);
        } catch (ObjectNotKnown e) {
            log.fatal("unreachable code block in registerObjectInstance");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        return instanceName;
    }
    
    public String registerObjectInstance(String className, String instanceName)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished, ObjectAlreadyRegistered {
        log.trace("registerObjectInstance " + className + " " + instanceName);
        
        int classHandle;
        
        try {
            classHandle = rtiAmb.getObjectClassHandle(className);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            rtiAmb.registerObjectInstance(classHandle, instanceName);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in registerObjectInstance");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.error("save and restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        
        return instanceName;
    }
    
    public void sendReceiveOrder(HLAPacket packet)
            throws NameNotFound, FederateNotExecutionMember, InteractionClassNotPublished,
            InteractionParameterNotDefined, ObjectNotKnown, AttributeNotDefined, AttributeNotOwned {
        if (packet.isInteraction()) {
            injectInteraction(packet.getName(), packet.getFields(), null);
        } else {
            updateObject(packet.getName(), packet.getFields(), null);
        }
    }
    
    // private methods
    
    private boolean isExitCondition() {
        return receivedSimEnd || exitFlag;
    }
    
    private void joinFederationExecution()
            throws InterruptedException {
        boolean joinSuccessful = false;
        
        for (int i = 0; !joinSuccessful && i < configuration.getMaxReconnectAttempts(); i++) {
            if (i > 0) {
                log.info("next join attempt in " + configuration.getWaitReconnectMs() + " ms...");
                Thread.sleep(configuration.getWaitReconnectMs());
            }
            
            final String federateName = configuration.getFederateName();
            final String federationName = configuration.getFederationId();
            log.info("joining federation " + federationName + " as " + federateName + " (" + i + ")");
            try {
                rtiAmb.joinFederationExecution(federateName, federationName, fedAmb, null);
                joinSuccessful = true;
            } catch (FederationExecutionDoesNotExist e) {
                log.warn("federation execution does not exist: " + federationName);
            } catch (SaveInProgress e) {
                log.warn("failed to join federation: save in progress");
            } catch (RestoreInProgress e) {
                log.warn("failed to join federation: restore in progress");
            } catch (FederateAlreadyExecutionMember | RTIinternalError  | ConcurrentAccessAttempted e) {
                throw new RTIAmbassadorException(e);
            }
        }
    }
    
    private void enableAsynchronousDelivery() {
        try {
            log.info("enabling asynchronous delivery of receive order messages");
            rtiAmb.enableAsynchronousDelivery();
        } catch (AsynchronousDeliveryAlreadyEnabled e) {
            log.debug("asynchronous delivery already enabled");
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void enableTimeConstrained() {
        try {
            log.info("enabling time constrained");
            rtiAmb.enableTimeConstrained();
            while (fedAmb.isTimeConstrained() == false) {
                tick();
            }
        } catch (TimeConstrainedAlreadyEnabled e) {
            log.debug("time constrained already enabled");
        } catch (EnableTimeConstrainedPending e) {
            log.warn("multiple attempts made to enable time constrained mode");
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void enableTimeRegulation() {
        try {
            log.info("enabling time regulation");
            rtiAmb.enableTimeRegulation(
                    new DoubleTime(fedAmb.getLogicalTime()),
                    new DoubleTimeInterval(configuration.getLookAhead()));
            while (fedAmb.isTimeRegulating() == false) {
                tick();
            }
        } catch (TimeRegulationAlreadyEnabled e) {
            log.debug("time regulation already enabled");
        } catch (EnableTimeRegulationPending e) {
            log.warn("multiple attempts made to enable time regulation mode");
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void publishAndSubscribe() {
        log.trace("publishAndSubscribe");
        try {
            log.debug("publishing interactions");
            for (InteractionClassType interaction : objectModel.getPublishedInteractions()) {
                publishInteraction(objectModel.getClassPath(interaction));
            }
            log.debug("subscribing to interactions");
            for (InteractionClassType interaction : objectModel.getSubscribedInteractions()) {
                subscribeInteraction(objectModel.getClassPath(interaction));
            }
            log.debug("publishing object attributes");
            for (ObjectClassType object : objectModel.getPublishedObjects()) {
                Set<String> attributeNames = new HashSet<String>();
                for (AttributeType attribute : objectModel.getPublishedAttributes(object)) {
                    attributeNames.add(attribute.getName().getValue());
                }
                publishObject(objectModel.getClassPath(object), attributeNames);
            }
            log.debug("subscribing to object attributes");
            for (ObjectClassType object : objectModel.getSubscribedObjects()) {
                Set<String> attributeNames = new HashSet<String>();
                for (AttributeType attribute : objectModel.getSubscribedAttributes(object)) {
                    attributeNames.add(attribute.getName().getValue());
                }
                subscribeObject(objectModel.getClassPath(object), attributeNames);
            }
        } catch (NameNotFound | FederateNotExecutionMember | AttributeNotDefined e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void publishInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating HLA publication for the interaction " + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.publishInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            log.fatal("unreachable code block in publishInteraction");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void subscribeInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating HLA subscription for the interaction " + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.subscribeInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            log.fatal("unreachable code block in subscribeInteraction");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } catch (FederateLoggingServiceCalls e) {
            log.fatal("some strange unknown thing happened");
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void publishObject(String classPath, Set<String> attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        publishObject(classPath, attributes.toArray(new String[0]));
    }
    
    private void publishObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        log.trace("publishObject " + classPath + " " + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeSet = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
            for (String attribute : attributes) {
                int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
                attributeSet.add(attributeHandle);
            }
            rtiAmb.publishObjectClass(classHandle, attributeSet);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in publishObject");
            throw new RTIAmbassadorException(e);
        } catch (OwnershipAcquisitionPending e) {
            log.fatal("ownership acquisition service not implemented");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void subscribeObject(String classPath, Set<String> attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        subscribeObject(classPath, attributes.toArray(new String[0]));
    }
    
    private void subscribeObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        log.trace("subscribeObject " + classPath + " " + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeSet = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
            for (String attribute : attributes) {
                int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
                attributeSet.add(attributeHandle);
            }
            rtiAmb.subscribeObjectClassAttributes(classHandle, attributeSet);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in publishObject");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void notifyOfFederationJoin() {
        log.trace("notifyOfFederationJoin");
        if (!objectModel.getPublishedInteractions().contains(FEDERATE_JOIN)) {
            log.warn("not configured to publish " + FEDERATE_JOIN);
            try {
                publishInteraction(FEDERATE_JOIN);
            } catch (NameNotFound e) {
                log.error("federation doesn't recognize " + FEDERATE_JOIN);
                throw new RTIAmbassadorException(e);
            } catch (FederateNotExecutionMember e) {
                throw new RTIAmbassadorException(e);
            }
        }
        Map<String, String> params = new HashMap<String, String>();
        String federateId = String.format("%s-%s", configuration.getFederateName(), UUID.randomUUID());
        params.put("FederateId", federateId);
        params.put("FederateType", configuration.getFederateName());
        if (configuration.getIsLateJoiner()) {
            params.put("IsLateJoiner", "true");
        } else {
            params.put("IsLateJoiner", "false");
        }
        log.info("using federate ID " + federateId);
        try {
            injectInteraction(FEDERATE_JOIN, params, null); // does this need a timestamp ?
        } catch (NameNotFound | InteractionClassNotPublished e) {
            log.error("unreachable code block in notifyOfFederationJoin");
            throw new RTIAmbassadorException(e);
        } catch (InteractionParameterNotDefined e) {
            log.error("unexpected syntax for " + FEDERATE_JOIN);
            throw new RTIAmbassadorException(e);
        } catch (FederateNotExecutionMember e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void injectInteraction(String className, Map<String, String> parameters, Double timestamp)
            throws NameNotFound, FederateNotExecutionMember, InteractionClassNotPublished,
            InteractionParameterNotDefined {
        log.trace("injectInteraction " + className + " " + Arrays.toString(parameters.entrySet().toArray())
                + " " + timestamp);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(className);
            SuppliedParameters suppliedParameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                int parameterHandle = rtiAmb.getParameterHandle(entry.getKey(), classHandle);
                byte[] parameterValue = entry.getValue().getBytes(); // do we need to add \0 ?
                suppliedParameters.add(parameterHandle, parameterValue);
            }
            if (timestamp == null) {
                log.debug("sending receive order interaction " + className);
                rtiAmb.sendInteraction(classHandle, suppliedParameters, null);
            } else {
                log.debug("sending interaction " + className + " for t=" + timestamp);
                rtiAmb.sendInteraction(classHandle, suppliedParameters, null, new DoubleTime(timestamp));
            }
        } catch (RTIinternalError | ConcurrentAccessAttempted | InvalidFederationTime e) {
            throw new RTIAmbassadorException(e);
        } catch (InteractionClassNotDefined e) {
            log.fatal("unreachable code block in injectInteraction");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void updateObject(String instanceName, Map<String, String> attributes, Double timestamp)
            throws ObjectNotKnown, FederateNotExecutionMember, NameNotFound, AttributeNotDefined, AttributeNotOwned {
        log.trace("updateObject " + instanceName + " " + Arrays.toString(attributes.entrySet().toArray()) 
                + " " + timestamp);
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            int classHandle = rtiAmb.getObjectClass(instanceHandle);
            SuppliedAttributes suppliedAttributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                int attributeHandle = rtiAmb.getAttributeHandle(entry.getKey(), classHandle);
                byte[] attributeValue = entry.getValue().getBytes(); // do we need to add \0 ?
                suppliedAttributes.add(attributeHandle, attributeValue);
            }
            if (timestamp == null) {
                log.debug("sending receive order object " + instanceName);
                rtiAmb.updateAttributeValues(instanceHandle, suppliedAttributes, null);
            } else {
                log.debug("sending object " + instanceName + " for t=" + timestamp);
                rtiAmb.updateAttributeValues(instanceHandle, suppliedAttributes, null, new DoubleTime(timestamp));
            }
        } catch (RTIinternalError | ConcurrentAccessAttempted | InvalidFederationTime e) {
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in updateObject");
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void sendPublications() {
        Queue<HLAPacket> publications = injectionCallback.getPublications(fedAmb.getLogicalTime());
        double timestamp = fedAmb.getLogicalTime() + configuration.getLookAhead();
        log.debug("sending publications for t=" + timestamp);
        
        HLAPacket packet = null;
        while ((packet = publications.poll()) != null) {
            log.debug("on publication for " + packet.getName());
            if (packet.isObject()) {
                try {
                    updateObject(packet.getName(), packet.getFields(), timestamp);
                } catch (NameNotFound | FederateNotExecutionMember | ObjectNotKnown | AttributeNotDefined
                        | AttributeNotOwned e) {
                    throw new RTIAmbassadorException(e);
                }
            } else {
                try {
                    injectInteraction(packet.getName(), packet.getFields(), timestamp);
                } catch (NameNotFound | FederateNotExecutionMember | InteractionClassNotPublished
                        | InteractionParameterNotDefined e) {
                    throw new RTIAmbassadorException(e);
                }
            }
        }
    }
    
    private void handleSubscriptions() {
        try {
            Interaction receivedInteraction;
            while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
                int classHandle = receivedInteraction.getClassHandle();
                String interactionName = rtiAmb.getInteractionClassName(classHandle);
                Map<String, String> parameters = mapParameters(receivedInteraction);
                injectionCallback.receiveInteraction(fedAmb.getLogicalTime(), interactionName, parameters);
    
                if (interactionName.equals(SIMULATION_END)) {
                    receivedSimEnd = true;
                }
            }
    
            ObjectReflection receivedObjectReflection;
            while ((receivedObjectReflection = fedAmb.nextObjectReflection()) != null) {
                int classHandle = receivedObjectReflection.getClassHandle();
                String className = rtiAmb.getObjectClassName(classHandle);
                String instanceName = receivedObjectReflection.getInstanceName();
                Map<String, String> parameters = mapAttributes(classHandle, receivedObjectReflection);
                injectionCallback.receiveObject(fedAmb.getLogicalTime(), className, instanceName, parameters);
            }
    
            String removedObjectName;
            while ((removedObjectName = fedAmb.nextRemovedObjectName()) != null) {
                log.info("no longer receiving updates for object " + removedObjectName);
            }
        } catch (RTIinternalError | InteractionClassNotDefined | FederateNotExecutionMember | ObjectClassNotDefined e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private Map<String, String> mapParameters(Interaction receivedInteraction) {
        int interactionHandle = receivedInteraction.getClassHandle();
        Map<String, String> parameters = new HashMap<String, String>();
        try {
            for (int i = 0; i < receivedInteraction.getParameterCount(); i++) {
                int parameterHandle = receivedInteraction.getParameterHandle(i);
                String parameterName = rtiAmb.getParameterName(parameterHandle, interactionHandle);
                String parameterValue = receivedInteraction.getParameterValue(i);
                log.debug(parameterName + "=" + parameterValue);
                parameters.put(parameterName, parameterValue);
            }
        } catch (RTIinternalError | InteractionClassNotDefined | InteractionParameterNotDefined | FederateNotExecutionMember e) {
            throw new RTIAmbassadorException(e);
        }
        return parameters;
    }

    private Map<String, String> mapAttributes(int objectClassHandle, ObjectReflection receivedObjectReflection) {
        Map<String, String> attributes = new HashMap<String, String>();
        try {
            for (int i = 0; i < receivedObjectReflection.getAttributeCount(); i++) {
                int attributeHandle = receivedObjectReflection.getAttributeHandle(i);
                String attributeName = rtiAmb.getAttributeName(attributeHandle, objectClassHandle);
                String attributeValue = receivedObjectReflection.getAttributeValue(i);
                log.debug(attributeName + "=" + attributeValue);
                attributes.put(attributeName, attributeValue);
            }
        } catch (RTIinternalError | ObjectClassNotDefined | AttributeNotDefined | FederateNotExecutionMember e) {
            throw new RTIAmbassadorException(e);
        }
        return attributes;
    }
    
    private void advanceLogicalTime() {
        Double newLogicalTime = fedAmb.getLogicalTime() + configuration.getStepSize();
        log.info("advancing logical time to " + newLogicalTime);
        try {
            fedAmb.setTimeAdvancing();
            rtiAmb.timeAdvanceRequest(new DoubleTime(newLogicalTime));
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }
        while (fedAmb.isTimeAdvancing() == true) {
            tick();
        }
        log.info("advanced logical time to " + fedAmb.getLogicalTime());
    }
    
    private void resignFederationExecution() {
        log.info("resigning from the federation execution " + configuration.getFederationId());
        try {
            rtiAmb.resignFederationExecution(ResignAction.NO_ACTION);
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }
    }
}
