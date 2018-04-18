package gov.nist.hla.gateway;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ieee.standards.ieee1516._2010.AttributeType;
import org.ieee.standards.ieee1516._2010.InteractionClassType;
import org.ieee.standards.ieee1516._2010.ObjectClassType;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.hla.FederateAmbassador;
import gov.nist.hla.Interaction;
import gov.nist.hla.ObjectReflection;
import gov.nist.hla.gateway.exception.RTIAmbassadorException;
import gov.nist.hla.gateway.exception.UnsupportedServiceException;
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

/**
 * This class provides a simplified API for working with the Portico implementation of HLA. It contains public methods
 * to call the HLA object management services to send interactions and object updates into a federation, and invokes
 * the methods in the {@link GatewayCallback} used at construction during the federate life cycle.
 * <p>
 * The gateway federate has a firm life cycle. After construction and calling {@link #run}, it will join the
 * federation indicated in its configuration file. It will then block waiting for the federation to achieve both
 * the readyToPopuate and readyToRun synchronization points in the listed order. After synchronization, it will begin
 * logical time progression, which will loop until the federation sends an interaction that designates the simulation
 * end or the public method {@link #requestExit} is invoked. If it exits due to simulation end, it will block for a
 * final synchronization point readyToResign before it exits. Otherwise, it will skip synchronization and perform one
 * last logical time iteration before resignation.
 * <p>
 * This class is not thread safe. All of its public methods will throw runtime exceptions if invoked from another
 * thread. It is only safe to invoke public methods from the concrete implementation of {@link GatewayCallback} used
 * to construct the gateway federate.
 * <p>
 * A significant number of HLA services are not exposed in the public interface. The federation management, ownership
 * management, time management, and data distribution management services are not exposed through the public API. Both
 * the save/restore services and ownership transfer services are unimplemented, and this class will throw exceptions
 * if the federation attempts to invoke either service.
 * <p>
 * This class has trivial support for the configuration option isLateJoiner. A federate configured to join late will
 * ignore the three synchronization points readyToPopulate, readyToRun, and readyToResign. This will cause a deadlock
 * scenario if the federate joins at t=0 before the readyToRun synchronization point has been achieved. There is no
 * distinction between the {@link GatewayCallback#initializeSelf} and {@link GatewayCallback#initializeWithPeers}
 * callbacks for a late joiner, both will be called at the same time in the life cycle (right after joining).
 * 
 * @author Thomas Roth
 */
public class GatewayFederate {
    private static final Logger log = LogManager.getLogger();

    private static final String READY_TO_POPULATE = "readyToPopulate";
    private static final String READY_TO_RUN = "readyToRun";
    private static final String READY_TO_RESIGN = "readyToResign";
    
    private GatewayFederateConfig configuration;
    private GatewayCallback callback;
    private ObjectModel objectModel;

    private RTIambassador rtiAmb;
    private FederateAmbassador fedAmb;

    private boolean isRunning = false;
    private boolean hasTimeStarted = false;
    private boolean receivedSimEnd = false;
    private boolean exitFlag = false;

    private double lastRequestedTime;

    /**
     * Create an {@link GatewayFederateConfig} from a JSON configuration file that can be used to construct a gateway
     * federate instance.
     * 
     * @param filepath The absolute or relative filepath to the configuration file
     * @return An instance of a configuration class that can be used to construct an gateway federate
     * @throws IOException if the filepath cannot be parsed as valid JSON
     */
    public static GatewayFederateConfig readConfiguration(String filepath)
            throws IOException {
        log.info("reading JSON configuration file at " + filepath);
        File configFile = Paths.get(filepath).toFile();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFile, GatewayFederateConfig.class);
    }

    /**
     * Constructs a gateway federate using the given configuration that will yield control during {@link #run} to
     * the given {@link GatewayCallback}.
     * 
     * @param configuration A configuration instance created using {@link #readConfiguration}
     * @param callback A set of callback functions that will be invoked during {@link #run}
     */
    public GatewayFederate(GatewayFederateConfig configuration, GatewayCallback callback) {
        this.configuration = configuration;
        this.callback = callback;
        objectModel = new ObjectModel(configuration.getFomFilepath());
        
        try {
            rtiAmb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        fedAmb = new FederateAmbassador();
    }
    
    /**
     * Constructs a gateway federate using the given configuration and {@link ObjectModel} that will yield control
     * during {@link #run} to the given {@link GatewayCallback}. This method should only be used if the gateway
     * application defines a new class derived from {@link ObjectModel} with extended functionality.
     * 
     * @param configuration A configuration instance created using {@link #readConfiguration}
     * @param callback A set of callback functions that will be invoked during {@link #run}
     * @param objectModel The object model the gateway application will use during execution.
     */
    public GatewayFederate(GatewayFederateConfig configuration, GatewayCallback callback, ObjectModel objectModel) {
        this.configuration = configuration;
        this.callback = callback;
        this.objectModel = objectModel;
        
        try {
            rtiAmb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        }
        fedAmb = new FederateAmbassador();
    }

    /**
     * A blocking call that will execute the complete life cycle of the gateway federate. This method will exit when
     * either the federation sends an interaction that represents simulation end, or {@link #requestExit} is invoked.
     * For both exit conditions, one final logical time step will be executed before this method returns control.
     */
    public void run() {
        log.trace("run");

        if (isRunning) {
            throw new RuntimeException("gateway federate instance already running");
        }
        this.exitFlag = false;
        this.receivedSimEnd = false;
        this.hasTimeStarted = false;
        this.lastRequestedTime = 0;
        this.isRunning = true;

        try {
            joinFederationExecution();
        } catch (FederationExecutionDoesNotExist e) {
            log.fatal("unable to join federation: " + e.getMessage());
            return;
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

            callback.initializeSelf();
            if (!configuration.getIsLateJoiner()) {
                synchronize(READY_TO_POPULATE);
            }
            callback.initializeWithPeers();
            if (!configuration.getIsLateJoiner()) {
                synchronize(READY_TO_RUN);
            }
            this.hasTimeStarted = true;

            while (!isExitCondition()) {
                log.trace("run t=" + getLogicalTime());
                callback.doTimeStep(lastRequestedTime);
                advanceLogicalTime();
            }

            if (!configuration.getIsLateJoiner() && receivedSimEnd) {
                synchronize(READY_TO_RESIGN);
            }
            notifyOfFederationResign();
            resignFederationExecution();
        } catch (FederateNotExecutionMember | TimeAdvanceAlreadyInProgress e) {
            throw new RTIAmbassadorException("unreachable code", e);
        }
        callback.terminate();
        this.isRunning = false;
    }

    /**
     * Yield control to the RTI ambassador to handle any receive order messages in the local message queue. This call
     * will invoke {@link GatewayCallback#receiveInteraction} and {@link GatewayCallback#receiveObject} when
     * there are receive order interactions and object reflections in the incoming message queue.
     * 
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     */
    public void tick()
            throws FederateNotExecutionMember {
        try {
            rtiAmb.tick();
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
        handleSubscriptions();
    }

    /**
     * Object model accessor
     * 
     * @return The object model that corresponds to this gateway federate's current publications and subscriptions
     */
    public ObjectModel getObjectModel() {
        return objectModel;
    }

    /**
     * Logical time accessor
     * 
     * @return This gateway federate's current logical time step
     */
    public double getLogicalTime() {
        return fedAmb.getLogicalTime();
    }

    /**
     * Get the lowest value timestamp that can be used to send interactions and object updates.
     * 
     * @return A timestamp to use as a parameter for {@link #sendInteraction} and {@link #updateObject}
     */
    public double getTimeStamp() {
        return fedAmb.getLogicalTime() + configuration.getLookAhead();
    }

    /**
     * Check whether the local federate has begun its logical time progression loop. This method can be used in both
     * {@link GatewayCallback#receiveInteraction} and {@link GatewayCallback#receiveObject} to distinguish between
     * messages that arrive during logical time progression, and those that arrive during initialization.
     * 
     * @return True if the federation has achieved the synchronization point readyToRun
     */
    public boolean hasTimeStarted() {
        return this.hasTimeStarted;
    }

    /**
     * Request this class resign from its federation and return from {@link #run} after the next logical time step.
     */
    public void requestExit() {
        log.info("application requested exit");
        this.exitFlag = true;
    }

    /**
     * Create a new object instance in the current federation and assign it a random name.
     * 
     * @param className The full HLA object class to create a new instance for
     * @return The instance name for the newly created object
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws NameNotFound if className is not the full classpath of a known HLA object class
     * @throws ObjectClassNotPublished if className is not an object class this federate publishes
     */
    public String registerObjectInstance(String className)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished {
        log.trace("registerObjectInstance " + className);
        try {
            int classHandle = rtiAmb.getObjectClassHandle(className);
            int instanceHandle = rtiAmb.registerObjectInstance(classHandle);
            return rtiAmb.getObjectInstanceName(instanceHandle);
        } catch (ObjectClassNotDefined | ObjectNotKnown e) {
            // classHandle retrieved from the RTI ambassador
            // instanceHandle received from the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    /**
     * Create a new object instance in the current federation and assign it the given name.
     * 
     * @param className The full HLA object class to create a new instance for
     * @param instanceName The unique name to assign to the newly created instance
     * @return The instanceName parameter used to name the new object
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws NameNotFound if className is not the full classpath of a known HLA object class
     * @throws ObjectClassNotPublished if className is not an object class this federate publishes
     * @throws ObjectAlreadyRegistered if another object instance already exists named instanceName
     */
    public String registerObjectInstance(String className, String instanceName)
            throws FederateNotExecutionMember, NameNotFound, ObjectClassNotPublished, ObjectAlreadyRegistered {
        log.trace("registerObjectInstance " + className + " " + instanceName);
        try {
            int classHandle = rtiAmb.getObjectClassHandle(className);
            rtiAmb.registerObjectInstance(classHandle, instanceName);
            return instanceName;
        } catch (ObjectClassNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    /**
     * Delete an object instance created from a prior call to {@link #registerObjectInstance}
     * 
     * @param instanceName The unique name of the object instance to delete from the federation
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws ObjectNotKnown if instanceName does not refer to an existing HLA object in the federation
     * @throws DeletePrivilegeNotHeld if this federate was not the one who created the object named instanceName
     */
    public void deleteObjectInstance(String instanceName)
            throws FederateNotExecutionMember, ObjectNotKnown, DeletePrivilegeNotHeld {
        log.trace("deleteObjectInstance " + instanceName);
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            rtiAmb.deleteObjectInstance(instanceHandle, null);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    /**
     * Create and send a receive order interaction to the federation. Other federates can receive this interaction
     * during the same logical time step it is sent using an explicit call to {@link #tick}. Because the message will
     * take some time to deliver, {@link #tick} should be called multiple times in a loop until the desired receive
     * order message has been delivered to {@link GatewayCallback#receiveInteraction}.
     * <p>
     * The behavior of this function is undefined when using a subset of the interaction's available parameters. The
     * parameters map should always contain values for every interaction parameter.
     * 
     * @param className The full HLA interaction class name to send
     * @param parameters A map from parameter names to string values
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws NameNotFound if className is not a known HLA interaction class, or a key from parameters is not a valid
     *  parameter name for the className interaction
     * @throws InteractionClassNotPublished if this federate does not publish the interaction className
     */
    public void sendInteraction(String className, Map<String, String> parameters)
            throws FederateNotExecutionMember, NameNotFound, InteractionClassNotPublished {
        log.trace("sendInteraction " + className + " " + Arrays.toString(parameters.entrySet().toArray()));
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(className);
            Map<String, String> modifiedParameters = addRootParameters(className, parameters);
            SuppliedParameters suppliedParameters = convertToSuppliedParameters(classHandle, modifiedParameters);
            rtiAmb.sendInteraction(classHandle, suppliedParameters, null);
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // convertToSuppliedParameters returns valid parameters
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    /**
     * Create and send an interaction to the federation which other federates will receive once their logical time
     * exceeds the given timestamp. The value of timestamp must be greater than or equal to the value returned by
     * {@link #getTimeStamp}.
     * <p>
     * The behavior of this function is undefined when using a subset of the interaction's available parameters. The
     * parameters map should always contain values for every interaction parameter.
     * 
     * @param className The full HLA interaction class name to send
     * @param parameters A map from parameter names to string values
     * @param timestamp The logical time after which other federates should receive this interaction
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws NameNotFound if className is not a known HLA interaction class, or a key from parameters is not a valid
     *  parameter name for the className interaction
     * @throws InteractionClassNotPublished if this federate does not publish the interaction className
     * @throws InvalidFederationTime if this federate cannot send interactions to be delivered at the given timestamp
     */
    public void sendInteraction(String className, Map<String, String> parameters, double timestamp)
            throws FederateNotExecutionMember, NameNotFound, InteractionClassNotPublished, InvalidFederationTime {
        log.trace("sendInteraction " + className + " " + Arrays.toString(parameters.entrySet().toArray())
        + " " + timestamp);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(className);
            Map<String, String> modifiedParameters = addRootParameters(className, parameters);
            SuppliedParameters suppliedParameters = convertToSuppliedParameters(classHandle, modifiedParameters);
            rtiAmb.sendInteraction(classHandle, suppliedParameters, null, new DoubleTime(timestamp));
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // convertToSuppliedParameters returns valid parameters
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    /**
     * Send a receive order update to an existing and owned object instance. Other federates can receive this update
     * during the same logical time step it is sent using an explicit call to {@link #tick}. Because the message will
     * take some time to deliver, {@link #tick} should be called multiple times in a loop until the desired receive
     * order message has been delivered to {@link GatewayCallback#receiveObject}.
     * 
     * @param instanceName The object instance name returned from {@link #registerObjectInstance}
     * @param attributes A map from attribute names to string values
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws ObjectNotKnown if instanceName does not refer to an existing HLA object in the federation
     * @throws NameNotFound if a key from the attributes map is not a valid attribute name for the object instance
     * @throws AttributeNotOwned if this federate was not the one who created the object named instanceName
     */
    public void updateObject(String instanceName, Map<String, String> attributes)
            throws FederateNotExecutionMember, ObjectNotKnown, NameNotFound, AttributeNotOwned {
        log.trace("updateObject " + instanceName + " " + Arrays.toString(attributes.entrySet().toArray()));
        try {
            int instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
            int classHandle = rtiAmb.getObjectClass(instanceHandle);
            SuppliedAttributes suppliedAttributes = convertToSuppliedAttributes(classHandle, attributes);
            rtiAmb.updateAttributeValues(instanceHandle, suppliedAttributes, null);
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // convertToSuppliedAttributes returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    /**
     * Send an update to an existing and owned object instance which other federates will receive once their logical
     * time exceeds the given timestamp. The value of timestamp must be greater than or equal to the value returned by
     * {@link #getTimeStamp}.
     * 
     * @param instanceName The object instance name returned from {@link #registerObjectInstance}
     * @param attributes A map from attribute names to string values
     * @param timestamp The logical time after which other federates should receive this object update
     * @throws FederateNotExecutionMember if invoked before {@link #run} or if connection to the federation is lost
     * @throws ObjectNotKnown if instanceName does not refer to an existing HLA object in the federation
     * @throws NameNotFound if a key from the attributes map is not a valid attribute name for the object instance
     * @throws AttributeNotOwned if this federate was not the one who created the object named instanceName
     * @throws InvalidFederationTime if this federate cannot send object updates to be delivered at the given timestamp
     */
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
            // classHandle retrieved from the RTI ambassador
            // convertToSuppliedAttributes returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    private boolean isExitCondition() {
        return receivedSimEnd || exitFlag;
    }

    private void joinFederationExecution()
            throws InterruptedException, FederationExecutionDoesNotExist {
        log.trace("joinFederationExecution");
        final String federateName = configuration.getFederateName();
        final String federationName = configuration.getFederationId();
        boolean joinSuccessful = false;

        for (int i = 1; !joinSuccessful && i <= configuration.getMaxReconnectAttempts(); i++) {
            if (i > 1) {
                log.info("next join attempt in " + configuration.getWaitReconnectMs() + " ms...");
                Thread.sleep(configuration.getWaitReconnectMs());
            }

            log.info("joining federation " + federationName + " as " + federateName + " (attempt " + i + ")");
            try {
                rtiAmb.joinFederationExecution(federateName, federationName, fedAmb, null);
                joinSuccessful = true;
            } catch (FederationExecutionDoesNotExist e) {
                if (i == configuration.getMaxReconnectAttempts()) {
                    throw e;
                }
                log.warn("federation execution does not exist: " + federationName);
            } catch (SaveInProgress | RestoreInProgress e) {
                throw new UnsupportedServiceException("for federation save/restore", e);
            } catch (FederateAlreadyExecutionMember | RTIinternalError | ConcurrentAccessAttempted e) {
                throw new RTIAmbassadorException(e);
            }
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
    }

    private void enableAsynchronousDelivery()
            throws FederateNotExecutionMember {
        try {
            log.trace("enableAsynchronousDelivery");
            rtiAmb.enableAsynchronousDelivery();
            log.info("asynchronous delivery enabled");
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
            log.trace("enableTimeConstrained");
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
            log.trace("enableTimeRegulation");
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
            for (InteractionClassType interaction : objectModel.getPublishedInteractions()) {
                publishInteraction(objectModel.getClassPath(interaction));
            }
            for (InteractionClassType interaction : objectModel.getSubscribedInteractions()) {
                subscribeInteraction(objectModel.getClassPath(interaction));
            }
            for (ObjectClassType object : objectModel.getPublishedObjects()) {
                Set<String> attributeNames = objectModel.getPublishedAttributes(object).stream().
                        map(x -> x.getName().getValue()).
                        collect(Collectors.toSet());
                publishObject(objectModel.getClassPath(object), attributeNames.toArray(new String[0]));
            }
            for (ObjectClassType object : objectModel.getSubscribedObjects()) {
                Set<String> attributeNames = objectModel.getSubscribedAttributes(object).stream().
                        map(x -> x.getName().getValue()).
                        collect(Collectors.toSet());
                subscribeObject(objectModel.getClassPath(object), attributeNames.toArray(new String[0]));
            }
        } catch (NameNotFound e) {
            throw new RTIAmbassadorException("invalid object model", e);
        }
    }

    private void publishInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating publication for " + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.publishInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void subscribeInteraction(String classPath)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating subscription for " + classPath);
        try {
            int classHandle = rtiAmb.getInteractionClassHandle(classPath);
            rtiAmb.subscribeInteractionClass(classHandle);
        } catch (InteractionClassNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        } catch (FederateLoggingServiceCalls e) {
            throw new RTIAmbassadorException("cannot subscribe to Manager.Federate.Report.ReportServiceInvocation", e);
        }
    }

    private void publishObject(String classPath, String... attributes)
            throws NameNotFound, FederateNotExecutionMember {
        log.info("creating publication for " + classPath + " attributes " + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeHandleSet = convertToAttributeHandleSet(classHandle, attributes);
            rtiAmb.publishObjectClass(classHandle, attributeHandleSet);
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle retrieved from the RTI ambassador
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
        log.info("creating subscription for " + classPath + " attributes" + Arrays.toString(attributes));
        try {
            int classHandle = rtiAmb.getObjectClassHandle(classPath);
            AttributeHandleSet attributeHandleSet = convertToAttributeHandleSet(classHandle, attributes);
            rtiAmb.subscribeObjectClassAttributes(classHandle, attributeHandleSet);
            rtiAmb.tick(); // temp fix due to portico issue with duplicate discovery
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // convertToAttributeHandleSet returns valid attributes
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (SaveInProgress | RestoreInProgress e) {
            throw new UnsupportedServiceException("for federation save/restore", e);
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private AttributeHandleSet convertToAttributeHandleSet(int classHandle, String... attributes)
            throws ObjectClassNotDefined, NameNotFound, FederateNotExecutionMember, RTIinternalError {
        log.trace("convertToAttributeHandleSet " + classHandle + " " + Arrays.toString(attributes));
        AttributeHandleSet attributeHandles = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        for (String attribute : attributes) {
            int attributeHandle = rtiAmb.getAttributeHandle(attribute, classHandle);
            try {
                attributeHandles.add(attributeHandle);
            } catch (AttributeNotDefined e) {
                // attributeHandle retrieved from the RTI ambassador
                throw new RTIAmbassadorException("unreachable code", e);
            }
        }
        return attributeHandles;
    }

    private void notifyOfFederationJoin()
            throws FederateNotExecutionMember {
        log.trace("notifyOfFederationJoin");

        if (!objectModel.getPublishedInteractions().contains(objectModel.getInteraction(ObjectModel.FEDERATE_JOIN))) {
            log.warn("not configured to publish " + ObjectModel.FEDERATE_JOIN);
            return;
        }

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("FederateId", configuration.getFederateName());
        parameters.put("FederateType", configuration.getFederateName());
        if (configuration.getIsLateJoiner()) {
            parameters.put("IsLateJoiner", "true");
        } else {
            parameters.put("IsLateJoiner", "false");
        }

        try {
            sendInteraction(ObjectModel.FEDERATE_JOIN, parameters); // does this need a timestamp ?
        } catch (InteractionClassNotPublished e) {
            // FEDERATE_JOIN is in the published interactions set
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (NameNotFound e) {
            throw new RTIAmbassadorException("unexpected parameters for " + ObjectModel.FEDERATE_JOIN, e);
        }
    }

    private void notifyOfFederationResign()
            throws FederateNotExecutionMember {
        log.trace("notifyOfFederationResign");

        if (!objectModel.getPublishedInteractions().contains(objectModel.getInteraction(ObjectModel.FEDERATE_RESIGN))) {
            log.warn("not configured to publish " + ObjectModel.FEDERATE_RESIGN);
            return;
        }

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("FederateId", configuration.getFederateName());
        parameters.put("FederateType", configuration.getFederateName());
        if (configuration.getIsLateJoiner()) {
            parameters.put("IsLateJoiner", "true");
        } else {
            parameters.put("IsLateJoiner", "false");
        }

        try {
            sendInteraction(ObjectModel.FEDERATE_RESIGN, parameters); // does this need a timestamp ?
        } catch (InteractionClassNotPublished e) {
            // FEDERATE_RESIGN is in the published interactions set
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (NameNotFound e) {
            throw new RTIAmbassadorException("unexpected parameters for " + ObjectModel.FEDERATE_RESIGN, e);
        }
    }

    private void advanceLogicalTime()
            throws TimeAdvanceAlreadyInProgress, FederateNotExecutionMember {
        lastRequestedTime = fedAmb.getLogicalTime() + configuration.getStepSize();
        log.info("advancing logical time to " + lastRequestedTime);
        try {
            fedAmb.setTimeAdvancing();
            rtiAmb.timeAdvanceRequest(new DoubleTime(lastRequestedTime));
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
    }

    private void resignFederationExecution() throws FederateNotExecutionMember {
        log.info("resigning from the federation execution " + configuration.getFederationId());
        try {
            rtiAmb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
        } catch (InvalidResignAction e) {
            // ResignAction.NO_ACTION is defined in Portico
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (FederateOwnsAttributes e) {
            throw new RTIAmbassadorException(e); // does Portico use this?
        } catch (RTIinternalError | ConcurrentAccessAttempted e) {
            throw new RTIAmbassadorException(e);
        }
    }

    private void handleSubscriptions()
            throws FederateNotExecutionMember {
        handleReceivedInteractions();
        handleDiscoveredObjectInstances();
        handleReceivedObjectReflections();
        handleRemovedObjectInstances();
    }

    private void handleReceivedInteractions()
            throws FederateNotExecutionMember {
        try {
            Interaction receivedInteraction;
            while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
                int classHandle = receivedInteraction.getClassHandle();
                String interactionName = rtiAmb.getInteractionClassName(classHandle);
                Map<String, String> parameters = convertToMap(receivedInteraction);
                callback.receiveInteraction(lastRequestedTime, interactionName, parameters);

                if (interactionName.equals(ObjectModel.SIMULATION_END)) {
                    receivedSimEnd = true;
                    log.info("received " + ObjectModel.SIMULATION_END);
                }
            }
        } catch (InteractionClassNotDefined | InteractionParameterNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // federate ambassador returns valid parameter names
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    private void handleReceivedObjectReflections()
            throws FederateNotExecutionMember {
        try {
            ObjectReflection receivedObjectReflection;
            while ((receivedObjectReflection = fedAmb.nextObjectReflection()) != null) {
                int classHandle = receivedObjectReflection.getClassHandle();
                String className = rtiAmb.getObjectClassName(classHandle);
                String instanceName = receivedObjectReflection.getInstanceName();
                Map<String, String> attributes = convertToMap(classHandle, receivedObjectReflection);
                callback.receiveObject(lastRequestedTime, className, instanceName, attributes);
            }
        } catch (ObjectClassNotDefined | AttributeNotDefined e) {
            // classHandle retrieved from the RTI ambassador
            // federate ambassador returns valid attribute names
            throw new RTIAmbassadorException("unreachable code", e);
        } catch (RTIinternalError e) {
            throw new RTIAmbassadorException(e);
        } 
    }

    private void handleDiscoveredObjectInstances()
            throws FederateNotExecutionMember {
        String instanceName;
        int instanceHandle;
        
        String className;
        int classHandle;

        // request updates for all subscribed attributes of discovered instances managed by the portico RTI
        //  this is not exposed through the gateway callbacks because our federates do not support this HLA service
        while ((instanceName = fedAmb.nextDiscoveredObjectName()) != null) {
            try {
                try {
                    instanceHandle = rtiAmb.getObjectInstanceHandle(instanceName);
                } catch (ObjectNotKnown e) {
                    log.warn("object instance {} removed before discovery", instanceName);
                    continue;
                }
                classHandle = rtiAmb.getObjectClass(instanceHandle);
                className = rtiAmb.getObjectClassName(classHandle);
                
                if (className.startsWith(ObjectModel.OBJECT_MOM + ".")) {
                    log.info("discovered RTI managed object {} ({})", instanceName, className);

                    // get a set of subscribed attribute names from the object model
                    ObjectClassType objectClassType = objectModel.getObject(className);
                    Set<AttributeType> subscribedAttributes = objectModel.getSubscribedAttributes(objectClassType);
                    Set<String> subscribedAttributeNames = subscribedAttributes.stream().
                            map( attribute -> attribute.getName().getValue()).
                            collect(Collectors.toSet());

                    AttributeHandleSet requestedAttributes =
                            convertToAttributeHandleSet(classHandle, subscribedAttributeNames.toArray(new String[0]));
                    rtiAmb.requestObjectAttributeValueUpdate(instanceHandle, requestedAttributes);
                    log.debug("requested updates for attributes {}", subscribedAttributeNames);
                }
                log.debug("processed discovered instance {} ({})", instanceName, className);
            } catch (RTIinternalError | ConcurrentAccessAttempted e) {
                throw new RTIAmbassadorException(e);
            } catch (ObjectNotKnown | ObjectClassNotDefined | AttributeNotDefined e) {
                // object instance handle retrieved from the RTI ambassador
                // object class handle retrieved from the RTI ambassador
                // attribute handles retrieved from the RTI ambassador
                throw new RTIAmbassadorException("unreachable code", e);
            } catch (SaveInProgress | RestoreInProgress e) {
                throw new UnsupportedServiceException("for federation save/restore", e);
            } catch (NameNotFound e) {
                throw new RTIAmbassadorException("bad object model", e);
            }
        }
    }

    private void handleRemovedObjectInstances() {
        String removedObjectName;
        while ((removedObjectName = fedAmb.nextRemovedObjectName()) != null) {
            log.info("no longer receiving updates for object " + removedObjectName);
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
            attributes.put(attributeName, attributeValue);
        }
        return attributes;
    }

    private Map<String, String> addRootParameters(String className, Map<String, String> parameters) {
        log.trace("addRootParameters " + className + " " + parameters.toString());
        Map<String, String> modifiedParameters = new HashMap<String, String>(parameters);
        if (className.contains(ObjectModel.INTERACTION_CPSWT)) {
            modifiedParameters.putIfAbsent("sourceFed", configuration.getFederateName());
            modifiedParameters.putIfAbsent("originFed", configuration.getFederateName());
            modifiedParameters.putIfAbsent("federateFilter", "");
            modifiedParameters.putIfAbsent("actualLogicalGenerationTime", Double.toString(0.0));
            log.debug("added {} parameters to {}", ObjectModel.INTERACTION_CPSWT, className);
        }
        return modifiedParameters;
    }

    private SuppliedParameters convertToSuppliedParameters(int classHandle, Map<String, String> parameters)
            throws FederateNotExecutionMember, InteractionClassNotDefined, NameNotFound, RTIinternalError {
        log.trace("convertToSuppliedParameters " + classHandle + " " + parameters.toString());
        SuppliedParameters suppliedParameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            int parameterHandle = rtiAmb.getParameterHandle(entry.getKey(), classHandle);
            byte[] parameterValue = entry.getValue().getBytes(); // do we need to add \0 ?
            suppliedParameters.add(parameterHandle, parameterValue);
        }
        return suppliedParameters;
    }

    private SuppliedAttributes convertToSuppliedAttributes(int classHandle, Map<String, String> attributes)
            throws FederateNotExecutionMember, ObjectClassNotDefined, NameNotFound, RTIinternalError {
        log.trace("convertToSuppliedAttributes " + classHandle + " " + attributes.toString());
        SuppliedAttributes suppliedAttributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            int attributeHandle = rtiAmb.getAttributeHandle(entry.getKey(), classHandle);
            byte[] attributeValue = entry.getValue().getBytes(); // do we need to add \0 ?
            suppliedAttributes.add(attributeHandle, attributeValue);
        }
        return suppliedAttributes;
    }
}
