package gov.nist.hla.ii;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cpswt.hla.SynchronizationPoints;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.ieee.standards.ieee1516._2010.AttributeType1;
import org.ieee.standards.ieee1516._2010.DocumentRoot;
import org.ieee.standards.ieee1516._2010.InteractionClassType;
import org.ieee.standards.ieee1516._2010.ObjectClassType;
import org.ieee.standards.ieee1516._2010.ObjectModelType;
import org.ieee.standards.ieee1516._2010.SharingEnumerations;
import org.ieee.standards.ieee1516._2010._2010Package;
import org.ieee.standards.ieee1516._2010.util._2010ResourceFactoryImpl;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeFactory;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import gov.nist.hla.ii.exception.PropertyNotAssigned;
import gov.nist.hla.ii.exception.PropertyNotFound;
import gov.nist.hla.ii.exception.RTIAmbassadorException;
import gov.nist.sds4emf.Deserialize;
import hla.rti.AsynchronousDeliveryAlreadyEnabled;
import hla.rti.AttributeHandleSet;
import hla.rti.AttributeNotDefined;
import hla.rti.AttributeNotOwned;
import hla.rti.ConcurrentAccessAttempted;
import hla.rti.EnableTimeConstrainedPending;
import hla.rti.EnableTimeRegulationPending;
import hla.rti.FederateLoggingServiceCalls;
import hla.rti.FederateNotExecutionMember;
import hla.rti.FederationExecutionDoesNotExist;
import hla.rti.InteractionClassNotDefined;
import hla.rti.InteractionClassNotPublished;
import hla.rti.InteractionParameterNotDefined;
import hla.rti.InvalidFederationTime;
import hla.rti.LogicalTime;
import hla.rti.LogicalTimeFactory;
import hla.rti.NameNotFound;
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
public class InjectionFederate implements Runnable {
	private static final Logger log = LogManager.getLogger(InjectionFederate.class);

	private static final String SIMULATION_END = "InteractionRoot.C2WInteractionRoot.SimulationControl.SimEnd";

	private static final int MAX_JOIN_ATTEMPTS = 6;
	private static final int REJOIN_DELAY_MS = 10000;

	public static final String INTERACTION_NAME_ROOT = "InteractionRoot.C2WInteractionRoot";
	public static final String OBJECT_NAME_ROOT = "ObjectRoot";

	public enum State {
		CONSTRUCTED, INITIALIZED, JOINED, TERMINATING;
	}

	private State state = State.CONSTRUCTED;
	private Double currTime = 0D;

	private Map<String, Integer> registeredObjects = new HashMap<String, Integer>();

	RTIambassador getRtiAmb() {
		return rtiAmb;
	}

	FederateAmbassador getFedAmb() {
		return fedAmb;
	}

	private RTIambassador rtiAmb;
	private FederateAmbassador fedAmb;
	private ObjectModelType fom;

	// set of object names that have been created as injectable entities
	private HashSet<String> discoveredObjects = new HashSet<String>();

	private String federateName;
	private String federationName;
	private static String fomFilePath;

	private InterObjInjection interObjectInjection;

	private InterObjReception interObjectReception;

	private AtomicBoolean advancing = new AtomicBoolean(false);

	public String getFomFilePath() {
		return fomFilePath;
	}

	private double lookahead;

	public State getState() {
		return state;
	}

	public ObjectModelType getFom() {
		return fom;
	}

	public String getFederateName() {
		return federateName;
	}

	public String getFederationName() {
		return federationName;
	}

	public double getStepsize() {
		return stepsize;
	}

	private double stepsize;

	public InjectionFederate() throws RTIAmbassadorException, ParserConfigurationException {

		try {
			rtiAmb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();
		} catch (RTIinternalError e) {
			throw new RTIAmbassadorException(e);
		}
		fedAmb = new FederateAmbassador();
	}

	public void init() {
		if (state != State.INITIALIZED) {
			throw new IllegalStateException("execute cannot be called in the current state: " + state.name());
		}

		try {
			joinFederationExecution();
			changeState(State.JOINED);

			enableAsynchronousDelivery();
			enableTimeConstrained();
			enableTimeRegulation();
			publishAndSubscribe();

		} catch (InterruptedException | RTIAmbassadorException e) {
			log.error(e);
		}
	}

	public void loadConfiguration(String filepath) throws IOException, PropertyNotFound, PropertyNotAssigned {
		if (state != State.CONSTRUCTED && state != State.INITIALIZED) {
			throw new IllegalStateException("loadConfiguration cannot be called in the current state: " + state.name());
		}

		log.debug("loading FIWARE federate configuration");
		Configuration configuration = new Configuration(filepath);

		federateName = configuration.getRequiredProperty("federate-name");
		log.debug("federate-name=" + federateName);
		federationName = configuration.getRequiredProperty("federation");
		log.debug("federation=" + federationName);
		fomFilePath = configuration.getRequiredProperty("fom-file");
		log.debug("fom-file=" + fomFilePath);
		lookahead = configuration.getRequiredPropertyAsDouble("lookahead");
		log.debug("lookahead=" + lookahead);
		stepsize = configuration.getRequiredPropertyAsDouble("stepsize");
		log.debug("stepsize=" + stepsize);

		fom = loadFOM();

		changeState(State.INITIALIZED);
	}

	private void changeState(State newState) {
		if (newState != state) {
			log.info("state change from " + state.name() + " to " + newState.name());
			state = newState;
		}
	}

	public void run() {
		log.trace("run==>");
		try {
			synchronize(SynchronizationPoints.ReadyToPopulate);
			synchronize(SynchronizationPoints.ReadyToRun);
		} catch (RTIAmbassadorException e) {
			log.error(e);
		}
		try {
			log.info("enter while==>");
			while (state != State.TERMINATING) {

				handleMessages();
				processIntObjs(currTime);
				advanceLogicalTime();
			}
		} catch (RTIAmbassadorException e) {
			log.error(e);
		} finally {
			try {
				switch (state) {
				case TERMINATING:
					synchronize(SynchronizationPoints.ReadyToResign);
					resignFederationExecution();
					break;
				case JOINED:
					resignFederationExecution();
					break;
				default:
					break;
				}
			} catch (Exception e) {
				log.warn("failed to resign federation execution", e);
			}
		}
	}

	private void processIntObjs(Double currTime) {
		Queue<InterObjDef> interactions = null;
		if (currTime == null) {
			interactions = interObjectInjection.getPreSynchInteractions();
		} else {
			interactions = interObjectInjection.getPublications(currTime);
		}

		InterObjDef def = null;
		while ((def = interactions.poll()) != null) {
			log.trace("def=" + def);
			if (def.getType() == InterObjDef.TYPE.OBJECT) {
				updateObject(def);
			} else {
				injectInteraction(def, currTime);
			}
		}
	}

	private void handleMessages() throws RTIAmbassadorException {

		boolean receivedNothing = true;
		try {
			Interaction receivedInteraction;
			while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
				// log.trace("receivedInteraction=" + receivedInteraction);
				receivedNothing = false;
				int interactionHandle = receivedInteraction.getInteractionClassHandle();
				String interactionName = rtiAmb.getInteractionClassName(interactionHandle);
				Map<String, String> parameters = mapParameters(receivedInteraction);
				interObjectReception.receiveInteraction(currTime, interactionName, parameters);

				if (interactionName.equals(SIMULATION_END)) {
					changeState(State.TERMINATING);
				}
			}

			ObjectReflection receivedObjectReflection;
			while ((receivedObjectReflection = fedAmb.nextObjectReflection()) != null) {
				// log.trace("receivedObjectReflection=" +
				// receivedObjectReflection);
				receivedNothing = false;
				int objectClassHandle = receivedObjectReflection.getObjectClass();
				String objectClassName = rtiAmb.getObjectClassName(objectClassHandle);
				String objectName = receivedObjectReflection.getObjectName();
				Map<String, String> parameters = mapAttributes(objectClassHandle, receivedObjectReflection);
				interObjectReception.receiveObject(currTime, objectClassName, objectName, parameters);
			}

			String removedObjectName;
			while ((removedObjectName = fedAmb.nextRemovedObjectName()) != null) {
				if (discoveredObjects.remove(removedObjectName) == false) {
					log.warn("tried to delete an unknown object instance with name=" + removedObjectName);
				} else {
					log.info("deleting context broker entity with id=" + removedObjectName);
				}
			}

			if (receivedNothing && !advancing.get()) {
				LogicalTime ft = getRtiAmb().queryFederateTime();
				DoubleTime dt = new DoubleTime(currTime);
				boolean lt = dt.isLessThan(ft);
				boolean eq = dt.isEqualTo(ft);
				boolean gt = dt.isGreaterThan(ft);
				interObjectReception.receiveInteraction(currTime,
						String.format("lt=%b eq=%b gt=%b %s %s", lt, eq, gt, ft.toString(), "Nothing received!!"),
						new HashMap<String, String>());
			}
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
	}

	Map<String, String> mapParameters(Interaction receivedInteraction) {
		int interactionHandle = receivedInteraction.getInteractionClassHandle();
		Map<String, String> parameters = null;
		try {
			parameters = new HashMap<String, String>();
			for (int i = 0; i < receivedInteraction.getParameterCount(); i++) {
				int parameterHandle = receivedInteraction.getParameterHandle(i);
				String parameterName = rtiAmb.getParameterName(parameterHandle, interactionHandle);
				String parameterValue = receivedInteraction.getParameterValue(i);
				log.debug(parameterName + "=" + parameterValue);
				parameters.put(parameterName, parameterValue);
			}
		} catch (RTIexception e) {
			log.error("", e);
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
		} catch (RTIexception e) {
			log.error("", e);
		}
		return attributes;
	}

	private void tick() throws RTIAmbassadorException {
		try {
			rtiAmb.tick();
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
		// handleMessages();
	}

	private void joinFederationExecution() throws InterruptedException, RTIAmbassadorException {
		boolean joinSuccessful = false;

		for (int i = 0; !joinSuccessful && i < MAX_JOIN_ATTEMPTS; i++) {
			if (i > 0) {
				log.info("next join attempt in " + REJOIN_DELAY_MS + " ms...");
				Thread.sleep(REJOIN_DELAY_MS);
			}

			try {
				log.info("joining federation " + federationName + " as " + federateName + " (" + i + ")");
				rtiAmb.joinFederationExecution(federateName, federationName, fedAmb, null);
				joinSuccessful = true;
			} catch (FederationExecutionDoesNotExist e) {
				log.warn("federation execution does not exist: " + federationName);
			} catch (SaveInProgress e) {
				log.warn("failed to join federation: save in progress");
			} catch (RestoreInProgress e) {
				log.warn("failed to join federation: restore in progress");
			} catch (RTIexception e) {
				throw new RTIAmbassadorException(e);
			}
		}
	}

	// enable Receive Order messages during any tick call
	public void enableAsynchronousDelivery() throws RTIAmbassadorException {
		try {
			log.info("enabling asynchronous delivery of receive order messages");
			rtiAmb.enableAsynchronousDelivery();
		} catch (AsynchronousDeliveryAlreadyEnabled e) {
			log.info("asynchronous delivery already enabled");
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
	}

	private void enableTimeConstrained() throws RTIAmbassadorException {
		try {
			log.info("enabling time constrained");
			rtiAmb.enableTimeConstrained();
			while (fedAmb.isTimeConstrained() == false) {
				tick();
			}
		} catch (TimeConstrainedAlreadyEnabled e) {
			log.info("time constrained already enabled");
		} catch (EnableTimeConstrainedPending e) {
			log.warn("multiple attempts made to enable time constrained mode");
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
	}

	private void enableTimeRegulation() throws RTIAmbassadorException {
		try {
			log.info("enabling time regulation");
			rtiAmb.enableTimeRegulation(new DoubleTime(fedAmb.getLogicalTime()), new DoubleTimeInterval(lookahead));
			while (fedAmb.isTimeRegulating() == false) {
				tick();
			}
		} catch (TimeRegulationAlreadyEnabled e) {
			log.info("time regulation already enabled");
		} catch (EnableTimeRegulationPending e) {
			log.warn("multiple attempts made to enable time regulation mode");
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
	}

	ObjectModelType loadFOM() {
		Deserialize.associateExtension("xml", new _2010ResourceFactoryImpl());
		Deserialize.registerPackage(_2010Package.eNS_URI, _2010Package.eINSTANCE);
		DocumentRoot docRoot = (DocumentRoot) Deserialize.it(fomFilePath);
		return docRoot.getObjectModel();
	}

	private void publishAndSubscribe() {
		int handle = 0;
		for (InteractionClassType classType : getInteractionSubscribe()) {
			log.info("creating HLA subscription for the interaction=" + classType.getName().getValue());
			try {
				handle = rtiAmb.getInteractionClassHandle(classType.getName().getValue());
				rtiAmb.subscribeInteractionClass(handle);
			} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError | InteractionClassNotDefined
					| FederateLoggingServiceCalls | SaveInProgress | RestoreInProgress | ConcurrentAccessAttempted e) {
				log.error("Continuing", e);
			}
		}
		for (InteractionClassType classType : getInteractionPublish()) {
			log.info("creating HLA publication for the interaction=" + classType.getName().getValue());
			try {
				handle = rtiAmb.getInteractionClassHandle(classType.getName().getValue());
				rtiAmb.publishInteractionClass(handle);
			} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError | InteractionClassNotDefined
					| SaveInProgress | RestoreInProgress | ConcurrentAccessAttempted e) {
				log.error("Continuing", e);
			}
		}
		for (ObjectClassType classType : getObjectSubscribe()) {
			log.info("creating HLA subscription for the object=" + classType.getName().getValue());
			try {
				String nname = formatObjectName(classType.getName().getValue());
				log.info("creating HLA subscription for the object1=" + nname);
				int objectHandle = rtiAmb.getObjectClassHandle(nname);
				AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
				for (AttributeType1 attribute : classType.getAttribute()) {
					int attributeHandle = rtiAmb.getAttributeHandle(attribute.getName().getValue(), objectHandle);
					attributes.add(attributeHandle);
				}
				rtiAmb.subscribeObjectClassAttributes(objectHandle, attributes);
			} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError | SaveInProgress | RestoreInProgress
					| ConcurrentAccessAttempted | ObjectClassNotDefined | AttributeNotDefined e) {
				log.error("Continuing", e);
			}
		}
		for (ObjectClassType classType : getObjectPublish()) {
			log.info("creating HLA publication for the object=" + classType.getName().getValue());
			try {
				String className = formatObjectName(classType.getName().getValue());
				int classHandle = rtiAmb.getObjectClassHandle(className);
				log.info("creating HLA publication for the object1=" + className);
				AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
				for (AttributeType1 attribute : classType.getAttribute()) {
					int attributeHandle = rtiAmb.getAttributeHandle(attribute.getName().getValue(), classHandle);
					attributes.add(attributeHandle);
				}
				rtiAmb.publishObjectClass(classHandle, attributes);
			} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError | SaveInProgress | RestoreInProgress
					| ConcurrentAccessAttempted | ObjectClassNotDefined | AttributeNotDefined
					| OwnershipAcquisitionPending e) {
				log.error("Continuing", e);
			}
		}
	}

	public void injectInteraction(InterObjDef def, Double logicalTime) {
		injectInteraction(def.getName(), def.getParameters(), logicalTime);
	}

	public void injectInteraction(String interactionName, Map<String, String> parameters, Double logicalTime) {
		int interactionHandle;
		try {
			interactionHandle = rtiAmb.getInteractionClassHandle(interactionName);
			log.debug("interactionName=" + interactionName);
			log.debug("interactionHandle=" + interactionHandle);
			SuppliedParameters suppliedParameters = assembleParameters(interactionHandle, parameters);
			if (logicalTime == null) {
				rtiAmb.sendInteraction(interactionHandle, suppliedParameters, generateTag());
			} else {
				LogicalTimeFactory ltf = new DoubleTimeFactory();
				LogicalTime lt = ltf.decode(this.convertToByteArray(logicalTime + lookahead), 0);
				rtiAmb.sendInteraction(interactionHandle, suppliedParameters, generateTag(), lt);
			}
		} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError e) {
			log.error("", e);
		} catch (InteractionClassNotDefined e) {
			log.error("", e);
		} catch (InteractionClassNotPublished e) {
			log.error("", e);
		} catch (InteractionParameterNotDefined e) {
			log.error("", e);
		} catch (SaveInProgress e) {
			log.error("", e);
		} catch (RestoreInProgress e) {
			log.error("", e);
		} catch (ConcurrentAccessAttempted e) {
			log.error("", e);
		} catch (InvalidFederationTime e) {
			log.error("", e);
		} catch (hla.rti.CouldNotDecode e) {
			log.error("", e);
		}
	}

	private byte[] convertToByteArray(double value) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putDouble(value);
		return buffer.array();

	}

	public SuppliedParameters assembleParameters(int interactionHandle, Map<String, String> parameters) {
		SuppliedParameters suppliedParameters = null;
		try {
			suppliedParameters = RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				int parameterHandle = rtiAmb.getParameterHandle(entry.getKey(), interactionHandle);
				byte[] parameterValue = entry.getValue().getBytes();
				suppliedParameters.add(parameterHandle, parameterValue);
			}
		} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError e) {
			log.error("", e);
		} catch (InteractionClassNotDefined e) {
			log.error("", e);
		}
		return suppliedParameters;
	}

	public void updateObject(InterObjDef def) {
		int classHandle = -1;
		int objectHandle = -1;
		try {
			classHandle = getRtiAmb().getObjectClassHandle(def.getName());
			objectHandle = getRtiAmb().registerObjectInstance(classHandle);
			updateObject(classHandle, objectHandle, def.getParameters());
		} catch (NullPointerException | FederateNotExecutionMember | RTIinternalError | NameNotFound
				| ObjectClassNotDefined | ObjectClassNotPublished | SaveInProgress | RestoreInProgress
				| ConcurrentAccessAttempted e) {
			log.debug("registeredObjects=" + registeredObjects);
			log.debug("def=" + def);
			log.debug("classHandle=" + classHandle);
			log.debug("objectHandle=" + objectHandle);
			log.error(e);
		}
	}

	public void updateObject(int classHandle, int objectHandle, Map<String, String> attributes) {
		try {
			SuppliedAttributes suppliedAttributes = assembleAttributes(classHandle, attributes);
			log.debug("suppliedAttributes=" + suppliedAttributes.size());
			rtiAmb.updateAttributeValues(objectHandle, suppliedAttributes, generateTag());
		} catch (FederateNotExecutionMember | RTIinternalError e) {
			log.error("", e);
		} catch (AttributeNotDefined e) {
			log.error("", e);
		} catch (SaveInProgress e) {
			log.error("", e);
		} catch (RestoreInProgress e) {
			log.error("", e);
		} catch (ConcurrentAccessAttempted e) {
			log.error("", e);
		} catch (ObjectNotKnown e) {
			log.error("", e);
		} catch (AttributeNotOwned e) {
			log.error("", e);
		} finally {
			log.debug("objectHandle=" + objectHandle);
		}
	}

	public SuppliedAttributes assembleAttributes(int classHandle, Map<String, String> attributes) {
		SuppliedAttributes suppliedAttributes = null;
		try {
			suppliedAttributes = RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
			for (Map.Entry<String, String> entry : attributes.entrySet()) {
				int attributeHandle = rtiAmb.getAttributeHandle(entry.getKey(), classHandle);
				byte[] attributeValue = entry.getValue().getBytes();
				suppliedAttributes.add(attributeHandle, attributeValue);
			}
		} catch (RTIinternalError e) {
			log.error("", e);
		} catch (ObjectClassNotDefined e) {
			log.error("", e);
		} catch (NameNotFound e) {
			log.error("", e);
		} catch (FederateNotExecutionMember e) {
			log.error("", e);
		}
		return suppliedAttributes;
	}

	public int publishInteraction(InterObjDef def) {
		return publishInteraction(def.getName());
	}

	public int publishInteraction(String interactionName) {
		int classHandle = 0;
		try {
			classHandle = getRtiAmb().getInteractionClassHandle(interactionName);
			getRtiAmb().publishInteractionClass(classHandle);
		} catch (InteractionClassNotDefined | FederateNotExecutionMember | SaveInProgress | RestoreInProgress
				| RTIinternalError | ConcurrentAccessAttempted | NameNotFound e) {
			log.error(e);
		}
		return classHandle;
	}

	public int publishObject(InterObjDef def) {
		return publishObject(def.getName(), def.getParameters());
	}

	public int publishObject(String objectName, Map<String, String> attrMap) {
		String[] attrs = (String[]) attrMap.keySet().toArray(new String[0]);
		return publishObject(objectName, attrs);
	}

	public int publishObject(String objectName, String... attributes) {
		int classHandle = 0;
		try {
			classHandle = rtiAmb.getObjectClassHandle(objectName);
			AttributeHandleSet attributeSet = RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
			for (String attrName : attributes) {
				int attributeHandle = rtiAmb.getAttributeHandle(attrName, classHandle);
				attributeSet.add(attributeHandle);
			}
			rtiAmb.publishObjectClass(classHandle, attributeSet);
		} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError e) {
			log.error(e);
		} catch (ObjectClassNotDefined e) {
			log.error("", e);
		} catch (AttributeNotDefined e) {
			log.error("", e);
		} catch (OwnershipAcquisitionPending e) {
			log.error("", e);
		} catch (SaveInProgress e) {
			log.error("", e);
		} catch (RestoreInProgress e) {
			log.error("", e);
		} catch (ConcurrentAccessAttempted e) {
			log.error("", e);
		}
		return classHandle;
	}

	public void registerObject(String className, int classHandle) {
		try {
			Integer objectHandle = registeredObjects.get(className);
			if (objectHandle == null) {
				objectHandle = rtiAmb.registerObjectInstance(classHandle);
				registeredObjects.put(className, objectHandle);
				log.debug("registerObject classHandle=" + classHandle);
				log.debug("registerObject className=" + className);
				log.debug("registerObject objectHandle=" + objectHandle);
			}
		} catch (FederateNotExecutionMember | RTIinternalError e) {
			log.error("", e);
		} catch (ObjectClassNotDefined e) {
			log.error("", e);
		} catch (ObjectClassNotPublished e) {
			log.error("", e);
		} catch (SaveInProgress e) {
			log.error("", e);
		} catch (RestoreInProgress e) {
			log.error("", e);
		} catch (ConcurrentAccessAttempted e) {
			log.error("", e);
		}
	}

	private byte[] generateTag() {
		return ("" + System.currentTimeMillis()).getBytes();
	}

	private void synchronize(String label) throws RTIAmbassadorException {
		log.info("waiting for announcement of the synchronization point " + label);
		while (fedAmb.isSynchronizationPointPending(label)) {
			tick();
		}

		try {
			rtiAmb.synchronizationPointAchieved(label);
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}

		log.info("waiting for federation to synchronize on synchronization point " + label);
		while (!fedAmb.isSynchronizationPointPending(label)) {
			handleMessages();
			processIntObjs(null);
			tick();
		}
		log.info("federation synchronized on " + label);
	}

	public Double advanceLogicalTime() throws RTIAmbassadorException {
		advancing.set(true);
		currTime = fedAmb.getLogicalTime() + stepsize;
		log.info("advancing logical time to " + currTime);
		try {
			fedAmb.setTimeAdvancing();
			rtiAmb.timeAdvanceRequest(new DoubleTime(currTime));
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
		while (fedAmb.isTimeAdvancing() == true) {
			tick();
		}
		log.info("advanced logical time to " + fedAmb.getLogicalTime());
		advancing.set(false);
		return currTime;
	}

	private void resignFederationExecution() throws RTIAmbassadorException {
		log.info("resigning from the federation execution " + federationName);
		try {
			rtiAmb.resignFederationExecution(ResignAction.NO_ACTION);
		} catch (RTIexception e) {
			throw new RTIAmbassadorException(e);
		}
	}

	public double getLBTS() {
		LogicalTime lbtsTime = null;
		boolean timeNotAcquired = true;
		while (timeNotAcquired) {
			try {
				synchronized (rtiAmb) {
					lbtsTime = rtiAmb.queryLBTS();
				}
				timeNotAcquired = false;
			} catch (FederateNotExecutionMember f) {
				log.error("SynchronizedFederate:  getLBTS:  ERROR:  Federate not execution member");
				log.error(f);
				return -1;
			} catch (Exception e) {
				log.error("SynchronizedFederate:  getLBTS:  Exception caught:  " + e.getMessage());
				log.error(e);
				return -1;
			}
		}

		DoubleTime doubleTime = new DoubleTime();
		doubleTime.setTo(lbtsTime);
		return doubleTime.getTime();
	}

	public Set<InteractionClassType> getInteractionSubscribe() {
		Set<InteractionClassType> set = new HashSet<InteractionClassType>();
		for (InteractionClassType itr : getFom().getInteractions().getInteractionClass().getInteractionClass()) {
			getInteractions(set, itr, SharingEnumerations.SUBSCRIBE);
		}

		return set;
	}

	public Set<InteractionClassType> getInteractionPublish() {
		Set<InteractionClassType> set = new HashSet<InteractionClassType>();
		for (InteractionClassType itr : getFom().getInteractions().getInteractionClass().getInteractionClass()) {
			getInteractions(set, itr, SharingEnumerations.PUBLISH);
		}

		return set;
	}

	public Set<InteractionClassType> getInteractions(Set<InteractionClassType> set, InteractionClassType itr,
			SharingEnumerations pubsub) {
		if (itr.getSharing() != null) {
			if (itr.getSharing().getValue() == pubsub
					|| itr.getSharing().getValue() == SharingEnumerations.PUBLISH_SUBSCRIBE) {
				set.add(itr);
				log.trace("added InteractionClassType.name=" + itr.getName().getValue() + "size=" + set.size());
			}
		}
		for (InteractionClassType itr1 : itr.getInteractionClass()) {
			getInteractions(set, itr1, pubsub);
		}
		return set;
	}

	public Set<ObjectClassType> getObjectSubscribe() {
		Set<ObjectClassType> set = new HashSet<ObjectClassType>();
		for (ObjectClassType oct : getFom().getObjects().getObjectClass().getObjectClass()) {
			log.debug("getObjectSubscribe=" + oct.getName().getValue());
			ObjectClassType oct1 = EcoreUtil.copy(oct);
			getObjects(set, oct1, SharingEnumerations.SUBSCRIBE);
		}

		return set;
	}

	public Set<ObjectClassType> getObjectPublish() {
		Set<ObjectClassType> set = new HashSet<ObjectClassType>();
		for (ObjectClassType oct : getFom().getObjects().getObjectClass().getObjectClass()) {
			log.debug("getObjectPublish=" + oct.getName().getValue());
			ObjectClassType oct1 = EcoreUtil.copy(oct);
			getObjects(set, oct1, SharingEnumerations.PUBLISH);
		}

		return set;
	}

	public ObjectClassType getObjects(Set<ObjectClassType> set, ObjectClassType oct, final SharingEnumerations pubsub) {
		log.debug("getObjects=" + oct.getName().getValue());
		Iterator<AttributeType1> itr = oct.getAttribute().iterator();
		while (itr.hasNext()) {
			AttributeType1 attr = itr.next();
			log.debug("processing AttributeType1.name==" + attr.getName().getValue());
			if (attr.getSharing() != null) {
				if (attr.getSharing().getValue() != pubsub
						&& attr.getSharing().getValue() != SharingEnumerations.PUBLISH_SUBSCRIBE) {
					itr.remove();
					log.trace("removed AttributeType1.name=" + attr.getName().getValue());
				}
			}
			if (!oct.getAttribute().isEmpty()) {
				set.add(oct);
				log.trace("added ObjectClassType.name=" + oct.getName().getValue() + " size=" + set.size());
			}
		}
		for (ObjectClassType oct1 : oct.getObjectClass()) {
			getObjects(set, oct1, pubsub);
		}
		return oct;
	}

	public InterObjInjection getInterObjectInjection() {
		return interObjectInjection;
	}

	public void setInterObjectInjection(InterObjInjection interObjectInjection) {
		this.interObjectInjection = interObjectInjection;
	}

	public InterObjReception getInterObjectReception() {
		return interObjectReception;
	}

	public void setInterObjectReception(InterObjReception interObjectReception) {
		this.interObjectReception = interObjectReception;
	}

	public AtomicBoolean getAdvancing() {
		log.trace("advancing=" + advancing.get());
		return advancing;
	}

	public String formatInteractionName(String interactionName) {
		return String.format("%s.%s", INTERACTION_NAME_ROOT, interactionName);
	}

	public String formatObjectName(String objectName) {
		return String.format("%s.%s", OBJECT_NAME_ROOT, objectName);
	}

	public double startLogicalTime() {
		advancing.set(true);
		return currTime;
	}

	Double getCurrTime() {
		return currTime;
	}

	void setCurrTime(Double currTime) {
		this.currTime = currTime;
	}

}
