package gov.nist.hla.ii;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

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
import hla.rti.LogicalTime;
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

// assume that SIMULATION_END is not sent as a Receive Order message
// no schema validation of name fields
// we cannot inject receive order messages
// we cannot inject future messages (at specific time)
// do we notify discovered/removed object instances?
public class InjectionFederate implements Runnable {
    private static final Logger log = LogManager.getLogger();

    private static final String SIMULATION_END = "InteractionRoot.C2WInteractionRoot.SimulationControl.SimEnd";
    private static final String FEDERATE_JOIN = "InteractionRoot.C2WInteractionRoot.FederateJoinInteraction";
    
    private InjectionFederateConfig configuration;
    private InjectionCallback injectionCallback;
    
    private RTIambassador rtiAmb;
    private FederateAmbassador fedAmb;
    private ObjectModel objectModel;
    
    private Map<String, Integer> registeredObjectsByName = new HashMap<String, Integer>();
    private Map<Integer, String> registeredObjectsByHandle = new HashMap<Integer, String>();
    
    private boolean receivedSimEnd = false;
    private boolean exitFlagSet = false;
    
    public static InjectionFederateConfig readConfiguration(String filepath)
            throws IOException {
        log.debug("reading JSON configuration file at " + filepath);
        Path configPath = Paths.get(filepath);
        File configFile = configPath.toFile();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFile, InjectionFederateConfig.class);
    }
    
    public InjectionFederate(InjectionFederateConfig configuration, InjectionCallback injectionCallback)
            throws ParserConfigurationException {
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
    
    public String getObjectInstanceName(int instanceHandle) {
        return registeredObjectsByHandle.get(instanceHandle);
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
    }
    
    public double getLogicalTime() {
        return fedAmb.getLogicalTime();
    }
    
    public void requestExit() {
        // it will take 1 iteration to exit
        log.debug("exit requested");
        this.exitFlagSet = true;
    }

    private boolean isExitCondition() {
        return receivedSimEnd || exitFlagSet;
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
            rtiAmb.enableTimeRegulation(new DoubleTime(fedAmb.getLogicalTime()),
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
        try {
            for (InteractionClassType classType : objectModel.getSubscribedInteractions()) {
                subscribeInteraction(objectModel.getClassPath(classType));
            }
            for (InteractionClassType classType : objectModel.getPublishedInteractions()) {
                publishInteraction(objectModel.getClassPath(classType));
            }
            for (ObjectClassType classType : objectModel.getSubscribedObjects()) {
                Set<String> attributeNames = new HashSet<String>();
                for (AttributeType attribute : objectModel.getSubscribedAttributes(classType)) {
                    attributeNames.add(attribute.getName().getValue());
                }
                subscribeObject(objectModel.getClassPath(classType), attributeNames);
            }
            for (ObjectClassType classType : objectModel.getPublishedObjects()) {
                Set<String> attributeNames = new HashSet<String>();
                for (AttributeType attribute : objectModel.getPublishedAttributes(classType)) {
                    attributeNames.add(attribute.getName().getValue());
                }
                publishObject(objectModel.getClassPath(classType), attributeNames);
            }
        } catch (NameNotFound | FederateNotExecutionMember | AttributeNotDefined e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void publishInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating HLA publication for the interaction=" + classPath);
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
        log.info("creating HLA subscription for the interaction=" + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.subscribeInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            log.fatal("unreachable code block in publishInteraction");
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

    public void publishObject(String classPath, Set<String> attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        publishObject(classPath, attributes.toArray(new String[0]));
    }
    
    public void publishObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeSet = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
            for (String attribute : attributes) {
                int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
                attributeSet.add(attributeHandle);
            }
            rtiAmb.subscribeObjectClassAttributes(classHandle, attributeSet);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in publishObject");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        }
    }
    
    public void subscribeObject(String classPath, Set<String> attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        subscribeObject(classPath, attributes.toArray(new String[0]));
    }
    
    public void subscribeObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember, AttributeNotDefined {
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeSet = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
            for (String attribute : attributes) {
                int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
                attributeSet.add(attributeHandle);
            }
            rtiAmb.publishObjectClass(classHandle, attributeSet);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } catch (ObjectClassNotDefined e) {
            log.fatal("unreachable code block in publishObject");
            throw new RTIAmbassadorException(e);
        } catch (OwnershipAcquisitionPending e) {
            log.fatal("ownership acquisition service not implemented");
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            log.fatal("save/restore services not supported");
            throw new RTIAmbassadorException(e);
        }
    }
    
    public void notifyOfFederationJoin() {
        if (!objectModel.getPublishedInteractions().contains(FEDERATE_JOIN)) {
            try {
                publishInteraction(FEDERATE_JOIN);
            } catch (NameNotFound | FederateNotExecutionMember e) {
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
        try {
            injectInteraction(FEDERATE_JOIN, params);
        } catch (NameNotFound | FederateNotExecutionMember | InteractionClassNotPublished
                | InteractionParameterNotDefined e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    public void injectInteraction(String interactionName, Map<String, String> parameters)
            throws NameNotFound, FederateNotExecutionMember, InteractionClassNotPublished, InteractionParameterNotDefined {
        try {
            int interactionHandle = rtiAmb.getInteractionClassHandle(interactionName);
            LogicalTime timestamp = new DoubleTime(fedAmb.getLogicalTime() + configuration.getLookAhead());
            SuppliedParameters suppliedParameters = assembleParameters(interactionHandle, parameters);
            rtiAmb.sendInteraction(interactionHandle, suppliedParameters, null, timestamp);
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
    
    // do we need to add \0 ?
    public SuppliedParameters assembleParameters(int interactionHandle, Map<String, String> parameters)
            throws RTIinternalError, InteractionClassNotDefined, NameNotFound, FederateNotExecutionMember {
        SuppliedParameters suppliedParameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            int parameterHandle = rtiAmb.getParameterHandle(entry.getKey(), interactionHandle);
            byte[] parameterValue = entry.getValue().getBytes();
            suppliedParameters.add(parameterHandle, parameterValue);
        }
        return suppliedParameters;
    }
    
    private void synchronize(String label) {
        log.info("waiting for announcement of the synchronization point " + label);
        while (!fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }

        try {
            synchronized (rtiAmb) {
                rtiAmb.synchronizationPointAchieved(label);
            }
        } catch (RTIexception e) {
            throw new RTIAmbassadorException(e);
        }

        log.info("waiting for federation to synchronize on synchronization point " + label);
        while (!fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }
        log.info("federation synchronized on " + label);
    }
    
    private void handleSubscriptions() {
        try {
            Interaction receivedInteraction;
            while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
                int interactionHandle = receivedInteraction.getClassHandle();
                String interactionName = rtiAmb.getInteractionClassName(interactionHandle);
                Map<String, String> parameters = mapParameters(receivedInteraction);
                injectionCallback.receiveInteraction(fedAmb.getLogicalTime(), interactionName, parameters);
    
                if (interactionName.equals(SIMULATION_END)) {
                    receivedSimEnd = true;
                }
            }
    
            ObjectReflection receivedObjectReflection;
            while ((receivedObjectReflection = fedAmb.nextObjectReflection()) != null) {
                int objectClassHandle = receivedObjectReflection.getClassHandle();
                String objectClassName = rtiAmb.getObjectClassName(objectClassHandle);
                String objectName = receivedObjectReflection.getInstanceName();
                Map<String, String> parameters = mapAttributes(objectClassHandle, receivedObjectReflection);
                injectionCallback.receiveObject(fedAmb.getLogicalTime(), objectClassName, objectName, parameters);
            }
    
            String removedObjectName;
            while ((removedObjectName = fedAmb.nextRemovedObjectName()) != null) {
                log.info("no longer receiving updates for object " + removedObjectName);
            }
        } catch (RTIinternalError | InteractionClassNotDefined | FederateNotExecutionMember | ObjectClassNotDefined e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    Map<String, String> mapParameters(Interaction receivedInteraction) {
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

    Map<String, String> mapAttributes(int objectClassHandle, ObjectReflection receivedObjectReflection) {
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
    
    private void sendPublications() {
        // make sure this returns 0.0 for pre-sync interactions!
        Queue<HLAPacket> publications = injectionCallback.getPublications(fedAmb.getLogicalTime());

        HLAPacket packet = null;
        while ((packet = publications.poll()) != null) {
            if (packet.isObject()) {
                try {
                    updateObject(packet);
                } catch (NameNotFound | FederateNotExecutionMember | ObjectNotKnown | AttributeNotDefined
                        | AttributeNotOwned e) {
                    throw new RTIAmbassadorException(e);
                }
            } else {
                try {
                    injectInteraction(packet);
                } catch (NameNotFound | FederateNotExecutionMember | InteractionClassNotPublished
                        | InteractionParameterNotDefined e) {
                    throw new RTIAmbassadorException(e);
                }
            }
        }
    }
    
    public void injectInteraction(HLAPacket packet)
            throws NameNotFound, FederateNotExecutionMember, InteractionClassNotPublished, InteractionParameterNotDefined {
        injectInteraction(packet.getName(), packet.getFields());
    }
    
    public void updateObject(HLAPacket packet)
            throws NameNotFound, FederateNotExecutionMember, ObjectNotKnown, AttributeNotDefined, AttributeNotOwned {
        updateObject(packet.getName(), packet.getFields());
    }

    public void updateObject(String objectName, Map<String, String> attributes)
            throws NameNotFound, FederateNotExecutionMember, ObjectNotKnown, AttributeNotDefined, AttributeNotOwned {
        try {
            int classHandle = rtiAmb.getObjectClassHandle(objectName);
            int objectHandle = rtiAmb.getObjectInstanceHandle(objectName);
            LogicalTime timestamp = new DoubleTime(fedAmb.getLogicalTime() + configuration.getLookAhead());
            SuppliedAttributes suppliedAttributes = assembleAttributes(classHandle, attributes);
            rtiAmb.updateAttributeValues(objectHandle, suppliedAttributes, null, timestamp);
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
    
    // do we need to add \0 ?
    public SuppliedAttributes assembleAttributes(int classHandle, Map<String, String> attributes)
            throws RTIinternalError, ObjectClassNotDefined, NameNotFound, FederateNotExecutionMember {
        SuppliedAttributes suppliedAttributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            int attributeHandle = rtiAmb.getAttributeHandle(entry.getKey(), classHandle);
            byte[] attributeValue = entry.getValue().getBytes();
            suppliedAttributes.add(attributeHandle, attributeValue);
        }
        return suppliedAttributes;
    }
    
    public void advanceLogicalTime() {
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
    
    private void tick() {
        try {
            rtiAmb.tick();
            handleSubscriptions();
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    public int createObjectInstance(String className)
            throws NameNotFound, ObjectClassNotPublished {
        int classHandle;
        int instanceHandle;
        String instanceName;
        
        try {
            classHandle = rtiAmb.getObjectClassHandle(className);
        } catch (FederateNotExecutionMember | RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            instanceHandle = rtiAmb.registerObjectInstance(classHandle);
        } catch (ObjectClassNotDefined | FederateNotExecutionMember | SaveInProgress | RestoreInProgress
                | RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            instanceName = rtiAmb.getObjectInstanceName(instanceHandle);
        } catch (ObjectNotKnown | FederateNotExecutionMember | RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        registeredObjectsByName.put(instanceName, instanceHandle);
        registeredObjectsByHandle.put(instanceHandle, instanceName);
        return instanceHandle;
    }
    
    public int createObjectInstance(String className, String instanceName)
            throws NameNotFound, ObjectClassNotPublished, ObjectAlreadyRegistered {
        int classHandle;
        int instanceHandle;
        
        try {
            classHandle = rtiAmb.getObjectClassHandle(className);
        } catch (FederateNotExecutionMember | RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        
        try {
            instanceHandle = rtiAmb.registerObjectInstance(classHandle, instanceName);
        } catch (ObjectClassNotDefined | FederateNotExecutionMember | SaveInProgress | RestoreInProgress
                | RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        
        registeredObjectsByName.put(instanceName, instanceHandle);
        registeredObjectsByHandle.put(instanceHandle, instanceName);
        return instanceHandle;
    }
    
    public ObjectModel getObjectModel() {
        return objectModel;
    }
}
