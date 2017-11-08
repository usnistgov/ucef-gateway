package gov.nist.hla.ii;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import gov.nist.hla.ii.exception.UnsupportedServiceException;
import hla.rti.AsynchronousDeliveryAlreadyEnabled;
import hla.rti.AttributeHandleSet;
import hla.rti.AttributeNotDefined;
import hla.rti.AttributeNotOwned;
import hla.rti.ConcurrentAccessAttempted;
import hla.rti.DeletePrivilegeNotHeld;
import hla.rti.EnableTimeConstrainedPending;
import hla.rti.EnableTimeRegulationPending;
import hla.rti.FederateAlreadyExecutionMember;
import hla.rti.FederateLoggingServiceCalls;
import hla.rti.FederateNotExecutionMember;
import hla.rti.FederateOwnsAttributes;
import hla.rti.FederationExecutionDoesNotExist;
import hla.rti.FederationTimeAlreadyPassed;
import hla.rti.InteractionClassNotDefined;
import hla.rti.InteractionClassNotPublished;
import hla.rti.InteractionParameterNotDefined;
import hla.rti.InvalidFederationTime;
import hla.rti.InvalidLookahead;
import hla.rti.InvalidResignAction;
import hla.rti.NameNotFound;
import hla.rti.ObjectAlreadyRegistered;
import hla.rti.ObjectClassNotDefined;
import hla.rti.ObjectClassNotPublished;
import hla.rti.ObjectNotKnown;
import hla.rti.OwnershipAcquisitionPending;
import hla.rti.RTIambassador;
import hla.rti.RTIinternalError;
import hla.rti.ResignAction;
import hla.rti.RestoreInProgress;
import hla.rti.SaveInProgress;
import hla.rti.SuppliedAttributes;
import hla.rti.SuppliedParameters;
import hla.rti.SynchronizationLabelNotAnnounced;
import hla.rti.TimeAdvanceAlreadyInProgress;
import hla.rti.TimeConstrainedAlreadyEnabled;
import hla.rti.TimeRegulationAlreadyEnabled;
import hla.rti.jlc.RtiFactoryFactory;

// no thread safe
// no schema validation
// feature limitations:
//  dynamic publish/subscribe
//  discover object instances
//  synchronize
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
        
        try {
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
                injectionCallback.afterTimeStep(getLogicalTime());
                advanceLogicalTime();
            }
            
            if (receivedSimEnd) {
                synchronize(SynchronizationPoints.ReadyToResign);
            }
            resignFederationExecution();
        } catch (FederateNotExecutionMember | TimeAdvanceAlreadyInProgress e) {
            throw new RTIAmbassadorException("unreachable code", e);
        }
        injectionCallback.afterResignation();
    }
    
    public void tick()
            throws FederateNotExecutionMember {
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
    
    public void requestExit() {
        log.info("application requested exit");
        this.exitFlag = true;
    }
    
    public String registerObjectInstance(String className)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished {
        log.trace("registerObjectInstance " + className);
        try {
            int classHandle = rtiAmb.getObjectClassHandle(className);
            int instanceHandle = rtiAmb.registerObjectInstance(classHandle);
            return rtiAmb.getObjectInstanceName(instanceHandle);
        } catch (ObjectClassNotDefined | ObjectNotKnown e) {
            // classHandle and instanceHandle set using the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    public String registerObjectInstance(String className, String instanceName)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished, ObjectAlreadyRegistered {
        log.trace("registerObjectInstance " + className + " " + instanceName);
        try {
            int classHandle = rtiAmb.getObjectClassHandle(className);
            rtiAmb.registerObjectInstance(classHandle, instanceName);
            return instanceName;
        } catch (ObjectClassNotDefined e) {
            // classHandle set using the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }
    
    public void deleteObjectInstance(String instanceName)
            throws FederateNotExecutionMember, ObjectNotKnown, DeletePrivilegeNotHeld {
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            rtiAmb.deleteObjectInstance(instanceHandle, null);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private SuppliedParameters convertToSuppliedParameters(int classHandle, Map<String, String> parameters)
            throws FederateNotExecutionMember, InteractionClassNotDefined, NameNotFound, RTIinternalError {
        SuppliedParameters suppliedParameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            int parameterHandle = rtiAmb.getParameterHandle(entry.getKey(), classHandle);
            byte[] parameterValue = entry.getValue().getBytes(); // do we need to add \0 ?
            suppliedParameters.add(parameterHandle, parameterValue);
        }
        return suppliedParameters;
    }
    
    public void injectInteraction(String className, Map<String, String> parameters)
            throws FederateNotExecutionMember, NameNotFound, InteractionClassNotPublished {
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(className);
            SuppliedParameters suppliedParameters = convertToSuppliedParameters(classHandle, parameters);
            rtiAmb.sendInteraction(classHandle, suppliedParameters, null);
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToSuppliedParameters returns valid parameters
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }
    
    public void injectInteraction(String className, Map<String, String> parameters, double timestamp)
            throws FederateNotExecutionMember, NameNotFound, InteractionClassNotPublished, InvalidFederationTime {
        log.trace("injectInteraction " + className + " " + Arrays.toString(parameters.entrySet().toArray())
                + " " + timestamp);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(className);
            SuppliedParameters suppliedParameters = convertToSuppliedParameters(classHandle, parameters);
            rtiAmb.sendInteraction(classHandle, suppliedParameters, null, new DoubleTime(timestamp));
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToSuppliedParameters returns valid parameters
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }
    
    private SuppliedAttributes convertToSuppliedAttributes(int classHandle, Map<String, String> attributes)
            throws FederateNotExecutionMember, ObjectClassNotDefined, NameNotFound, RTIinternalError {
        SuppliedAttributes suppliedAttributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            int attributeHandle = rtiAmb.getAttributeHandle(entry.getKey(), classHandle);
            byte[] attributeValue = entry.getValue().getBytes(); // do we need to add \0 ?
            suppliedAttributes.add(attributeHandle, attributeValue);
        }
        return suppliedAttributes;
    }
    
    public void updateObject(String instanceName, Map<String, String> attributes)
            throws FederateNotExecutionMember, ObjectNotKnown, NameNotFound, AttributeNotOwned {
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            int classHandle = rtiAmb.getObjectClass(instanceHandle);
            SuppliedAttributes suppliedAttributes = convertToSuppliedAttributes(classHandle, attributes);
            rtiAmb.updateAttributeValues(instanceHandle, suppliedAttributes, null);
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToSuppliedAttributes returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }
    
    public void updateObject(String instanceName, Map<String, String> attributes, double timestamp)
            throws FederateNotExecutionMember, ObjectNotKnown, NameNotFound, AttributeNotOwned, InvalidFederationTime {
        log.trace("updateObject " + instanceName + " " + Arrays.toString(attributes.entrySet().toArray()) 
                + " " + timestamp);
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            int classHandle = rtiAmb.getObjectClass(instanceHandle);
            SuppliedAttributes suppliedAttributes = convertToSuppliedAttributes(classHandle, attributes);
            rtiAmb.updateAttributeValues(instanceHandle, suppliedAttributes, null, new DoubleTime(timestamp));
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToSuppliedAttributes returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }
    
    private void synchronize(String label)
            throws FederateNotExecutionMember {
        log.info("waiting for announcement of the synchronization point " + label);
        while (!fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }

        try {
            rtiAmb.synchronizationPointAchieved(label);
        } catch (SynchronizationLabelNotAnnounced e) {
            // label found in pending synchronization points
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }

        log.info("waiting for federation to synchronize on synchronization point " + label);
        while (fedAmb.isSynchronizationPointPending(label)) {
            tick();
        }
        log.info("federation synchronized on " + label);
    }
    
    private boolean isExitCondition() {
        return receivedSimEnd || exitFlag;
    }
    
    private void joinFederationExecution()
            throws InterruptedException {
        final String federateName = configuration.getFederateName();
        final String federationName = configuration.getFederationId();
        boolean joinSuccessful = false;
        
        for (int i = 0; !joinSuccessful && i < configuration.getMaxReconnectAttempts(); i++) {
            if (i > 0) {
                log.info("next join attempt in " + configuration.getWaitReconnectMs() + " ms...");
                Thread.sleep(configuration.getWaitReconnectMs());
            }
            
            log.info("joining federation " + federationName + " as " + federateName + " (" + i + ")");
            try {
                rtiAmb.joinFederationExecution(federateName, federationName, fedAmb, null);
                joinSuccessful = true;
            } catch (FederationExecutionDoesNotExist e) {
                log.warn("federation execution does not exist: " + federationName);
            } catch (SaveInProgress | RestoreInProgress e) {
                throw new UnsupportedServiceException("for federation save/restore", e);
            } catch (FederateAlreadyExecutionMember | RTIinternalError | ConcurrentAccessAttempted e) {
                throw new RTIAmbassadorException(e);
            }
        }
    }
    
    private void enableAsynchronousDelivery()
            throws FederateNotExecutionMember {
        try {
            log.info("enabling asynchronous delivery of receive order messages");
            rtiAmb.enableAsynchronousDelivery();
        } catch (AsynchronousDeliveryAlreadyEnabled e) {
            log.debug("asynchronous delivery already enabled");
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void enableTimeConstrained()
            throws FederateNotExecutionMember, TimeAdvanceAlreadyInProgress {
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
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void enableTimeRegulation()
            throws FederateNotExecutionMember, TimeAdvanceAlreadyInProgress {
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
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (InvalidFederationTime | InvalidLookahead e) {
            throw new RTIAmbassadorException(e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void publishAndSubscribe()
            throws FederateNotExecutionMember {
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
                publishObject(objectModel.getClassPath(object), attributeNames.toArray(new String[0]));
            }
            log.debug("subscribing to object attributes");
            for (ObjectClassType object : objectModel.getSubscribedObjects()) {
                Set<String> attributeNames = new HashSet<String>();
                for (AttributeType attribute : objectModel.getSubscribedAttributes(object)) {
                    attributeNames.add(attribute.getName().getValue());
                }
                subscribeObject(objectModel.getClassPath(object), attributeNames.toArray(new String[0]));
            }
        } catch (NameNotFound e) {
            throw new RTIAmbassadorException("invalid object model", e);
        }
    }
    
    private void publishInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating HLA publication for the interaction " + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.publishInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            // classHandle set using the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
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
            // classHandle set using the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } catch (FederateLoggingServiceCalls e) {
            throw new RTIAmbassadorException("cannot subscribe to Manager.Federate.Report.ReportServiceInvocation", e);
        }
    }
    
    private AttributeHandleSet convertToAttributeHandleSet(int classHandle, String... attributes)
            throws ObjectClassNotDefined, NameNotFound, FederateNotExecutionMember, RTIinternalError {
        AttributeHandleSet attributeHandles = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        for (String attribute : attributes) {
            int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
            try {
                attributeHandles.add(attributeHandle);
            } catch (AttributeNotDefined e) {
                // attributeHandle set using the RTI ambassador
                throw new RTIAmbassadorException("unreachable code", e);
            }
        }
        return attributeHandles;
    }
        
    private void publishObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember {
        log.trace("publishObject " + classPath + " " + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeHandleSet = convertToAttributeHandleSet(classHandle, attributes);
            rtiAmb.publishObjectClass(classHandle, attributeHandleSet);
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToAttributeHandleSet returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (OwnershipAcquisitionPending e) {
            throw new UnsupportedServiceException("for ownership management", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void subscribeObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember {
        log.trace("subscribeObject " + classPath + " " + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeHandleSet = convertToAttributeHandleSet(classHandle, attributes);
            rtiAmb.subscribeObjectClassAttributes(classHandle, attributeHandleSet);
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle set using the RTI ambassador
            // convertToAttributeHandleSet returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private void notifyOfFederationJoin()
            throws FederateNotExecutionMember {
        log.trace("notifyOfFederationJoin");
        if (!objectModel.getPublishedInteractions().contains(FEDERATE_JOIN)) {
            log.warn("not configured to publish " + FEDERATE_JOIN);
            return;
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
            injectInteraction(FEDERATE_JOIN, params); // does this need a timestamp ?
        } catch (InteractionClassNotPublished e) {
            // FEDERATE_JOIN is in the published interactions set
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (NameNotFound e) {
            throw new RTIAmbassadorException("unexpected parameters for " + FEDERATE_JOIN, e);
        }
    }
    
    private void advanceLogicalTime()
            throws TimeAdvanceAlreadyInProgress, FederateNotExecutionMember {
        Double newLogicalTime = fedAmb.getLogicalTime() + configuration.getStepSize();
        log.info("advancing logical time to " + newLogicalTime);
        try {
            fedAmb.setTimeAdvancing();
            rtiAmb.timeAdvanceRequest(new DoubleTime(newLogicalTime));
        } catch (InvalidFederationTime | FederationTimeAlreadyPassed e) {
            throw new RTIAmbassadorException(e);
        } catch (EnableTimeRegulationPending | EnableTimeConstrainedPending e) {
            throw new RTIAmbassadorException(e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        while (fedAmb.isTimeAdvancing() == true) {
            tick();
        }
        log.info("advanced logical time to " + fedAmb.getLogicalTime());
    }
    
    private void resignFederationExecution() throws FederateNotExecutionMember {
        log.info("resigning from the federation execution " + configuration.getFederationId());
        try {
            rtiAmb.resignFederationExecution(ResignAction.NO_ACTION);
        } catch (InvalidResignAction e) {
            // ResignAction.NO_ACTION is defined in Portico
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (FederateOwnsAttributes e) {
            throw new RTIAmbassadorException(e); // does Portico use this?
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }
    
    private Map<String, String> convertToMap(Interaction receivedInteraction)
            throws InteractionClassNotDefined, InteractionParameterNotDefined, FederateNotExecutionMember, RTIinternalError {
        int interactionHandle = receivedInteraction.getClassHandle();
        Map<String, String> parameters = new HashMap<String, String>();
        for (int i = 0; i < receivedInteraction.getParameterCount(); i++) {
            int parameterHandle = receivedInteraction.getParameterHandle(i);
            String parameterName = rtiAmb.getParameterName(parameterHandle, interactionHandle);
            String parameterValue = receivedInteraction.getParameterValue(i);
            log.debug(parameterName + "=" + parameterValue);
            parameters.put(parameterName, parameterValue);
        }
        return parameters;
    }

    private Map<String, String> convertToMap(int objectClassHandle, ObjectReflection receivedObjectReflection)
            throws ObjectClassNotDefined, AttributeNotDefined, FederateNotExecutionMember, RTIinternalError {
        Map<String, String> attributes = new HashMap<String, String>();
        for (int i = 0; i < receivedObjectReflection.getAttributeCount(); i++) {
            int attributeHandle = receivedObjectReflection.getAttributeHandle(i);
            String attributeName = rtiAmb.getAttributeName(attributeHandle, objectClassHandle);
            String attributeValue = receivedObjectReflection.getAttributeValue(i);
            log.debug(attributeName + "=" + attributeValue);
            attributes.put(attributeName, attributeValue);
        }
        return attributes;
    }
    
    private void handleSubscriptions()
            throws FederateNotExecutionMember {
        try {
            Interaction receivedInteraction;
            while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
                int classHandle = receivedInteraction.getClassHandle();
                String interactionName = rtiAmb.getInteractionClassName(classHandle);
                Map<String, String> parameters = convertToMap(receivedInteraction);
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
                Map<String, String> parameters = convertToMap(classHandle, receivedObjectReflection);
                injectionCallback.receiveObject(fedAmb.getLogicalTime(), className, instanceName, parameters);
            }
    
            String removedObjectName;
            while ((removedObjectName = fedAmb.nextRemovedObjectName()) != null) {
                log.info("no longer receiving updates for object " + removedObjectName);
            }
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined | ObjectClassNotDefined | AttributeNotDefined e) {
            // the federate ambassador returns valid interactions
            // the federate ambassador returns valid object reflections
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        } 
    }
}
