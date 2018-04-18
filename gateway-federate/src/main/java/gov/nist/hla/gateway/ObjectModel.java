package gov.nist.hla.gateway;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.emf.ecore.EObject;
import org.ieee.standards.ieee1516._2010.AttributeType;
import org.ieee.standards.ieee1516._2010.DocumentRoot;
import org.ieee.standards.ieee1516._2010.InteractionClassType;
import org.ieee.standards.ieee1516._2010.ObjectClassType;
import org.ieee.standards.ieee1516._2010.ObjectModelType;
import org.ieee.standards.ieee1516._2010.ParameterType;
import org.ieee.standards.ieee1516._2010.SharingEnumerations;
import org.ieee.standards.ieee1516._2010.SharingType;
import org.ieee.standards.ieee1516._2010._2010Package;
import org.ieee.standards.ieee1516._2010.util._2010ResourceFactoryImpl;

import gov.nist.sds4emf.Deserialize;

/**
 * This class defines a set of helper functions that act on an Eclipse Modeling Framework (EMF) representation of a
 * Federation Object Model (FOM). The schema for the HLA object model was imported into EMF and used to generate a
 * set of Java classes. When an instance of this class is instantiated, a specific FOM file is parsed and stored as
 * a document tree using these generated Java classes. The public methods of this class define a set of queries that
 * can be performed on this document tree. The main function of this class is to provide access to the interactions
 * and objects defined in the FOM. 
 * 
 * @author Thomas Roth
 */
public class ObjectModel {
    public static final String INTERACTION_ROOT     = "InteractionRoot";
    public static final String C2W_INTERACTION_ROOT = INTERACTION_ROOT + ".C2WInteractionRoot";
    public static final String SIMULATION_END       = C2W_INTERACTION_ROOT + ".SimulationControl.SimEnd";
    public static final String FEDERATE_JOIN        = C2W_INTERACTION_ROOT + ".FederateJoinInteraction";
    public static final String FEDERATE_RESIGN      = C2W_INTERACTION_ROOT + ".FederateResignInteraction";
    
    private static final Logger log = LogManager.getLogger();
    
    private static boolean packageRegistered = false;
    
    private static ObjectModelType readObjectModel(String filepath) {
        if (!packageRegistered) {
            Deserialize.associateExtension("xml", new _2010ResourceFactoryImpl());
            Deserialize.registerPackage(_2010Package.eNS_URI, _2010Package.eINSTANCE);
            packageRegistered = true;
        }
        DocumentRoot documentRoot = (DocumentRoot) Deserialize.it(filepath);
        return documentRoot.getObjectModel();
    }
    
    private ObjectModelType objectModel;
    
    private Map<String, InteractionClassType> classPathToInteraction = new HashMap<String, InteractionClassType>();
    private Map<InteractionClassType, String> interactionToClassPath = new HashMap<InteractionClassType, String>();
    
    private Set<InteractionClassType> publishedInteractions = new HashSet<InteractionClassType>();
    private Set<InteractionClassType> subscribedInteractions = new HashSet<InteractionClassType>();
    
    private Map<InteractionClassType, Set<ParameterType>> interactionToParameters =
            new HashMap<InteractionClassType, Set<ParameterType>>();
    
    private Map<String, ObjectClassType> classPathToObject = new HashMap<String, ObjectClassType>();
    private Map<ObjectClassType, String> objectToClassPath = new HashMap<ObjectClassType, String>();
    
    private Set<ObjectClassType> publishedObjects = new HashSet<ObjectClassType>();
    private Set<ObjectClassType> subscribedObjects = new HashSet<ObjectClassType>();
    
    private Map<ObjectClassType, Set<AttributeType>> objectToAttributes =
            new HashMap<ObjectClassType, Set<AttributeType>>();
    private Map<ObjectClassType, Set<AttributeType>> publishedAttributes =
            new HashMap<ObjectClassType, Set<AttributeType>>();
    private Map<ObjectClassType, Set<AttributeType>> subscribedAttributes =
            new HashMap<ObjectClassType, Set<AttributeType>>();
    
    /**
     * Constructs a new document tree that mirrors the structure of the passed FOM file.
     * 
     * @param filepath Path to the Federation Object Model (FOM) XML file that represents the object model
     */
    public ObjectModel(String filepath) {
        log.info("creating object model from " + filepath);
        this.objectModel = ObjectModel.readObjectModel(filepath);
        initializeInteractionVariables();
        initializeObjectVariables();
    }
    
    /**
     * Get the InteractionClassType interface for the provided class path.
     * 
     * @param classPath The fully qualified class path for the interaction to retrieve
     * @return The InteractionClassType for the given classPath, or null if the interaction does not exist
     */
    public InteractionClassType getInteraction(String classPath) {
        return classPathToInteraction.get(classPath);
    }
    
    /**
     * Get the fully qualified HLA class path for an InteractionClassType. The InteractionClassType interface does
     * provide a method to get the interaction class name, but this isn't a fully qualified class path that includes
     * the interaction's ancestors. This method returns a full class path that starts with InteractionRoot.
     * 
     * @param interaction The interaction to convert into a class path string
     * @return The full class path for the given interaction, or null if the model doesn't contain the interaction.
     */
    public String getClassPath(InteractionClassType interaction) {
        return interactionToClassPath.get(interaction);
    }
    
    /**
     * Get the subset of interaction classes with sharing set to PUBLISH or PUBLISH_SUBSCRIBE.
     * 
     * @return An unmodifiable set of InteractionClassType interfaces that are marked as published in the object model
     */
    public Set<InteractionClassType> getPublishedInteractions() {
        return Collections.unmodifiableSet(publishedInteractions);
    }
    
    /**
     * Get the subset of interactions classes with sharing set to SUBSCRIBE or PUBLISH_SUBSCRIBE
     * 
     * @return An unmodifiable set of InteractionClassType interfaces that are marked as subscribed in the object model
     */
    public Set<InteractionClassType> getSubscribedInteractions() {
        return Collections.unmodifiableSet(subscribedInteractions);
    }
    
    /**
     * Get the full parameter set for an interaction. The InteractionClassType interface does provide a method to get
     * its immediate parameters, but this does not include parameters inherited from parent interactions. This method
     * is recursive and will return the full inherited parameter set.
     * 
     * @param interaction The interaction from which the parameters should be retrieved
     * @return An unmodifiable set of ParamaterType interfaces defined for the given interaction or its parents
     */
    public Set<ParameterType> getParameters(InteractionClassType interaction) {
        if (!interactionToParameters.containsKey(interaction)) {
            throw new IllegalArgumentException("invalid interaction class");
        }
        return Collections.unmodifiableSet(interactionToParameters.get(interaction));
    }
    
    /**
     * Get the model details for a specific interaction parameter using a string identifier.
     * 
     * @param interaction The interaction from which the parameter should be retrieved
     * @param parameterName The string identifier of the parameter to retrieve
     * @return The object model for the parameter, or null if the interaction does not contain the parameter
     */
    public ParameterType getParameter(InteractionClassType interaction, String parameterName) {
        for (ParameterType parameter : getParameters(interaction)) {
            if (parameter.getName() != null && parameter.getName().getValue().equals(parameterName)) {
                return parameter;
            }
        }
        return null;
    }
    
    /**
     * Checks if the passed interaction class is used in the {@link GatewayFederate} implementation. The gateway
     * handles some core interactions to communicate with federates generated from UCEF. These interactions might not
     * be useful to the gateway application, and this method can be used to filter them.
     * 
     * @param interaction An interaction from the object model to compare against the core interactions.
     * @return True if the interaction is used in the {@link GatewayFederate} implementation.
     */
    public boolean isCoreInteraction(InteractionClassType interaction) {
        final String classPath = getClassPath(interaction);
        
        switch (classPath) {
            case INTERACTION_ROOT:
            case C2W_INTERACTION_ROOT:
            case SIMULATION_END:
            case FEDERATE_JOIN:
            case FEDERATE_RESIGN:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get the ObjectClassType associated with the given class path.
     * 
     * @param classPath The fully qualified HLA class path for the object to retrieve
     * @return The ObjectClassType for the given classPath, or null if no such object exists in the model
     */
    public ObjectClassType getObject(String classPath) {
        return classPathToObject.get(classPath);
    }
    
    /**
     * Get the fully qualified HLA class path for an ObjectClassType instance. The ObjectClassType interface does
     * provide a method to get the object class, but this isn't a fully qualified class path that includes the object's
     * ancestors. This method returns a full class path that starts with ObjectRoot.
     * 
     * @param object The instance to convert into a class path string
     * @return The fully qualified class path for the given object, or null if the model does not contain the object
     */
    public String getClassPath(ObjectClassType object) {
        return objectToClassPath.get(object);
    }
    
    /**
     * Get a set of published object interfaces. Although object classes are not published like interaction classes,
     * the HLA standard specifies that the sharing field for each object class should be set to the union of its
     * attribute sharing fields. This method returns the object classes that contain at least one attribute with its
     * sharing field set to either PUBLISH or PUBLISH_SUBSCRIBE.
     * 
     * @return An unmodifiable set of interfaces for object classes with published attributes
     */
    public Set<ObjectClassType> getPublishedObjects() {
        return Collections.unmodifiableSet(publishedObjects);
    }
    
    /**
     * Get a set of subscribed object interfaces. Although object classes are not subscribed like interaction classes,
     * the HLA standard specifies that the sharing field for each object class should be set to the union of its
     * attribute sharing fields. This method returns the object classes that contain at least one attribute with its
     * sharing field set to either SUBSCRIBE or PUBLISH_SUBSCRIBE.
     * 
     * @return An unmodifiable set of interfaces for object classes with subscribed attributes
     */
    public Set<ObjectClassType> getSubscribedObjects() {
        return Collections.unmodifiableSet(subscribedObjects);
    }
    
    /**
     * Get a set of attribute interfaces associated with the given object class. Although ObjectClassType defines a
     * method to get its attributes, this method is not recursive and will not retrieve the attributes inherited
     * from parent object classes. This method will return all the defined and inherited attributes.
     * 
     * @param object The object class whose attributes should be retrieved
     * @return An unmodifiable set of attribute interfaces
     */
    public Set<AttributeType> getAttributes(ObjectClassType object) {
        if (!objectToAttributes.containsKey(object)) {
            throw new IllegalArgumentException("invalid object class");
        }
        return Collections.unmodifiableSet(objectToAttributes.get(object));
    }
    
    /**
     * Get the set of attributes associated with a given object that have a sharing of PUBLISH or PUBLISH_SUBSCRIBE.
     * 
     * @param object The object class whose attributes should be retrieved
     * @return A unmodifiable set of published attributes
     */
    public Set<AttributeType> getPublishedAttributes(ObjectClassType object) {
        if (!publishedAttributes.containsKey(object)) {
            throw new IllegalArgumentException("invalid object class");
        }
        return Collections.unmodifiableSet(publishedAttributes.get(object));
    }
    
    /**
     * Get the set of attributes associated with a given object that have a sharing of SUBSCRIBE or PUBLISH_SUBSCRIBE.
     * 
     * @param object The object class whose attributes should be retrieved
     * @return A unmodifiable set of subscribed attributes
     */
    public Set<AttributeType> getSubscribedAttributes(ObjectClassType object) {
        if (!subscribedAttributes.containsKey(object)) {
            throw new IllegalArgumentException("invalid object class");
        }
        return Collections.unmodifiableSet(subscribedAttributes.get(object));
    }
    
    /**
     *  Get the model details for a specific object attribute using a string identifier.
     *  
     * @param object The object from which the attribute should be retrieved
     * @param attributeName The string identifier of the attribute to retrieve
     * @return The object model for the specified attribute, or null the object does not contain such an attribute
     */
    public AttributeType getAttribute(ObjectClassType object, String attributeName) {
        for (AttributeType attribute : getAttributes(object)) {
            if (attribute.getName() != null && attribute.getName().getValue().equals(attributeName)) {
                return attribute;
            }
        }
        return null;
    }
    
    /**
     * Checks if the passed object class is used in the {@link GatewayFederate} implementation. The gateway may in the
     * future handle some core objects to communicate with federates generated from UCEF. These objects might not be
     * useful to the gateway application, and this method can be used to filter them.
     * 
     * @param object An object class from the object model to compare against the core objects.
     * @return True if the object class is used in the {@link GatewayFederate} implementation.
     */
    public boolean isCoreObject(ObjectClassType object) {
        return false; // there are no core objects in the current implementation
    }
    
    private boolean isPublish(SharingType sharingType) {
        if (sharingType == null) {
            return false;
        }
        SharingEnumerations sharingValue = sharingType.getValue();
        return sharingValue == SharingEnumerations.PUBLISH || sharingValue == SharingEnumerations.PUBLISH_SUBSCRIBE;
    }
    
    private boolean isSubscribe(SharingType sharingType) {
        if (sharingType == null) {
            return false;
        }
        SharingEnumerations sharingValue = sharingType.getValue();
        return sharingValue == SharingEnumerations.SUBSCRIBE || sharingValue == SharingEnumerations.PUBLISH_SUBSCRIBE;
    }
    
    private void initializeInteractionVariables() {
        log.trace("initializeInteractionVariables");
        
        InteractionClassType interactionRoot = objectModel.getInteractions().getInteractionClass();
        if (interactionRoot == null) {
            log.info("empty interactions table");
            return;
        }
        
        Queue<InteractionClassType> unprocessedInteractions = new LinkedList<InteractionClassType>();
        unprocessedInteractions.add(interactionRoot);
        
        while (!unprocessedInteractions.isEmpty()) {
            InteractionClassType nextInteraction = unprocessedInteractions.poll();
            processInteractionClass(nextInteraction);
            unprocessedInteractions.addAll(nextInteraction.getInteractionClass());
        }
    }
    
    private void processInteractionClass(InteractionClassType interaction) {
        log.trace("processInteractionClass " + interaction.getName().getValue());
        
        String classPath = expandClassPath(interaction);
        Set<ParameterType> parameters = expandParameters(interaction);
        
        interactionToClassPath.put(interaction, classPath);
        classPathToInteraction.put(classPath, interaction);
        
        if (isPublish(interaction.getSharing())) {
            log.trace("\tmarked as publication");
            publishedInteractions.add(interaction);
        }
        if (isSubscribe(interaction.getSharing())) {
            log.trace("\tmarked as subscription");
            subscribedInteractions.add(interaction);
        }
        
        interactionToParameters.put(interaction, parameters);
        
        log.debug("processed " + classPath + " with " + parameters.size() + " parameters");
    }
    
    private String expandClassPath(InteractionClassType interaction) {
        EObject parent = interaction.eContainer();
        if (parent != null && parent instanceof InteractionClassType) {
            InteractionClassType parentInteraction = (InteractionClassType) parent;
            return getClassPath(parentInteraction) + "." + interaction.getName().getValue();
        }
        return interaction.getName().getValue(); // no parent interaction class
    }
    
    private Set<ParameterType> expandParameters(InteractionClassType interaction) {
        Set<ParameterType> parameters = new HashSet<ParameterType>(interaction.getParameter());
        Set<String> definedParameterNames = parameters.stream().
                map(x -> x.getName().getValue()). // extract the value for parameter name
                collect(Collectors.toSet());
        log.trace("\twith parameters " + definedParameterNames.toString());
        
        EObject parent = interaction.eContainer();
        if (parent != null && parent instanceof InteractionClassType) {
            InteractionClassType parentInteraction = (InteractionClassType) parent;
            for (ParameterType parentParameter : getParameters(parentInteraction)) {
                String parameterName = parentParameter.getName().getValue();
                if (!definedParameterNames.contains(parameterName)) {
                    parameters.add(parentParameter);
                    log.trace("\twith inherited parameter " + parameterName);
                }
            }
        }
        return parameters;
    }
    
    private void initializeObjectVariables() {
        log.trace("initializeObjectVariables");
        
        ObjectClassType objectRoot = objectModel.getObjects().getObjectClass();
        if (objectRoot == null) {
            log.info("empty objects table");
            return;
        }
        
        Queue<ObjectClassType> unprocessedObjects = new LinkedList<ObjectClassType>();
        unprocessedObjects.add(objectRoot);
        
        while (!unprocessedObjects.isEmpty()) {
            ObjectClassType nextObject = unprocessedObjects.poll();
            processObjectClass(nextObject);
            unprocessedObjects.addAll(nextObject.getObjectClass());
        }
    }
    
    private void processObjectClass(ObjectClassType object) {
        log.trace("processObjectClass " + object.getName().getValue());

        String classPath = expandClassPath(object);
        Set<AttributeType> attributes = expandAttributes(object);
        Set<AttributeType> filteredPublications = attributes.stream().
                filter(p -> isPublish(p.getSharing())).
                collect(Collectors.toSet());
        Set<AttributeType> filteredSubscriptions = attributes.stream().
                filter(p -> isSubscribe(p.getSharing())).
                collect(Collectors.toSet());
        
        objectToClassPath.put(object, classPath);
        classPathToObject.put(classPath, object);
        
        if (isPublish(object.getSharing())) {
            log.trace("\tmarked as publication");
            publishedObjects.add(object);
        }
        if (isSubscribe(object.getSharing())) {
            log.trace("\tmarked as subscription");
            subscribedObjects.add(object);
        }
        
        objectToAttributes.put(object, attributes);
        publishedAttributes.put(object, filteredPublications);
        subscribedAttributes.put(object, filteredSubscriptions);
        
        log.debug("processed " + classPath + " with " + attributes.size() + " attributes");
    }
    
    private String expandClassPath(ObjectClassType object) {
        EObject parent = object.eContainer();
        if (parent != null && parent instanceof ObjectClassType) {
            ObjectClassType parentObject = (ObjectClassType) parent;
            return getClassPath(parentObject) + "." + object.getName().getValue();
        }
        return object.getName().getValue(); // no parent object class
    }
    
    private Set<AttributeType> expandAttributes(ObjectClassType object) {
        Set<AttributeType> attributes = new HashSet<AttributeType>(object.getAttribute());
        Set<String> definedAttributeNames = attributes.stream().
                map(x -> x.getName().getValue()). // extract the value for attribute name
                collect(Collectors.toSet());
        log.trace("\twith attributes " + definedAttributeNames.toString());
        
        EObject parent = object.eContainer();
        if (parent != null && parent instanceof ObjectClassType) {
            ObjectClassType parentObject = (ObjectClassType) parent;
            for (AttributeType attribute : getAttributes(parentObject)) {
                String attributeName = attribute.getName().getValue();
                if (!definedAttributeNames.contains(attributeName)) {
                    attributes.add(attribute);
                    log.trace("\twith inherited attribute " + attributeName);
                }
            }
        }
        return attributes;
    }
}
