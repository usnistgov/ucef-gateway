package gov.nist.hla.ii;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
    private static final Logger log = LogManager.getLogger();
    
    private static boolean packageRegistered = false;
    
    // need to replace these static calls with a better design
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
    private Map<InteractionClassType, Set<ParameterType>> interactionToParameters = new HashMap<InteractionClassType, Set<ParameterType>>();
    
    private Set<InteractionClassType> publishedInteractions = new HashSet<InteractionClassType>();
    private Set<InteractionClassType> subscribedInteractions = new HashSet<InteractionClassType>();
    
    private Map<String, ObjectClassType> classPathToObject = new HashMap<String, ObjectClassType>();
    private Map<ObjectClassType, String> objectToClassPath = new HashMap<ObjectClassType, String>();
    private Map<ObjectClassType, Set<AttributeType>> objectToAttributes = new HashMap<ObjectClassType, Set<AttributeType>>();
    
    private Set<ObjectClassType> publishedObjects = new HashSet<ObjectClassType>();
    private Set<ObjectClassType> subscribedObjects = new HashSet<ObjectClassType>();
    
    /**
     * Constructs a new document tree that mirrors the structure of the passed FOM file.
     * 
     * @param filepath Path to the Federation Object Model (FOM) that should be parsed to generate the object model
     */
    public ObjectModel(String filepath) {
        log.info("creating object model from " + filepath);
        this.objectModel = ObjectModel.readObjectModel(filepath);
        initializeInteractionVariables();
        initializeObjectVariables();
    }
    
    /**
     * Returns the full HLA class path for an InteractionClassType instance. The InteractionClassType interface does
     * provide a method to retrieve the interaction class, but this isn't a fully qualified class path that includes
     * the interaction's ancestors. This method returns a full class path that starts with InteractionRoot.
     * 
     * @param interaction The interaction object to convert into a class path string
     * @return The full class path for the interaction given as a parameter
     */
    public String getClassPath(InteractionClassType interaction) {
        return interactionToClassPath.get(interaction);
    }
    
    /**
     * Returns the InteractionClassType associated with the given class path.
     * 
     * @param classPath The full class path for the interaction to retrieve
     * @return The InteractionClassType in the model associated with the given classPath
     */
    public InteractionClassType getInteraction(String classPath) {
        return classPathToInteraction.get(classPath);
    }
    
    /**
     * Returns the subset of interactions in the FOM with sharing set to PUBLISH or PUBLISH_SUBSCRIBE.
     * 
     * @return A set of InteractionClassType interfaces that are marked as published in the object model
     */
    public Set<InteractionClassType> getPublishedInteractions() {
        return Collections.unmodifiableSet(publishedInteractions);
    }
    
    /**
     * Returns the subset of interactions in the FOM with sharing set to SUBSCRIBE or PUBLISH_SUBSCRIBE
     * 
     * @return A set of InteractionClassType interfaces that are marked as subscribed in the object model
     */
    public Set<InteractionClassType> getSubscribedInteractions() {
        return Collections.unmodifiableSet(subscribedInteractions);
    }
    
    /**
     * Returns the full parameter set for an interaction. The InteractionClassType does provide a method to retrieve
     * its immediate parameters, but this call will not retrieve parameters inherited from parent interactions. This
     * call is recursive and will return the full inherited parameter set.
     * 
     * @param interaction The interaction from which the parameters should be retrieved
     * @return A set of ParamaterType interfaces that are defined for the given interaction or its parents.
     */
    public Set<ParameterType> getParameters(InteractionClassType interaction) {
        return Collections.unmodifiableSet(interactionToParameters.get(interaction));
    }
    
    /**
     * Returns the model details for a specific interaction parameter using a string identifier.
     * 
     * @param interaction The interaction from which the parameter should be retrieved
     * @param parameterName The string identifier of the parameter to retrieve
     * @return The object model representation of the specified parameter.
     */
    public ParameterType getParameter(InteractionClassType interaction, String parameterName) {
        for (ParameterType parameter : getParameters(interaction)) {
            if (parameter.getName().getValue().equals(parameterName)) {
                return parameter;
            }
        }
        return null;
    }
    
    /**
     * Returns the full HLA class path for an ObjectClassType instance. The ObjectClassType interface does provide a
     * method to retrieve the object class, but this isn't a fully qualified class path that includes the object's
     * ancestors. This method returns a full class path that starts with ObjectRoot.
     * 
     * @param object The instance to convert into a class path string
     * @return The full class path for the object given as a parameter
     */
    public String getClassPath(ObjectClassType object) {
        return objectToClassPath.get(object);
    }
    
    /**
     * Returns the ObjectClassType associated with the given class path.
     * 
     * @param classPath The full class path for the object to retrieve
     * @return The ObjectClassType in the model associated with the given classPath
     */
    public ObjectClassType getObject(String classPath) {
        return classPathToObject.get(classPath);
    }
    
    /**
     * Get a set of published object interfaces. Although object classes are not published like interaction classes,
     * the HLA standard specifies that the FOM has a sharing field for each object class set to the union of its
     * attribute sharing fields. This method returns the object classes with sharing fields set to either PUBLISH or
     * PUBLISH_SUBSCRIBE.
     * 
     * @return An unmodifiable set of interfaces for published objects.
     */
    public Set<ObjectClassType> getPublishedObjects() {
        return Collections.unmodifiableSet(publishedObjects);
    }
    
    /**
     * Gets a set of subscribed object interfaces. Although object classes are not subscribed to like interactions, the
     * HLA standard specifies that the FOM has a sharing field for each object class that is set to the union of its
     * attribute fields. This method returns the object classes with sharing fields set to either SUBSCRIBE or
     * PUBLISH_SUBSCRIBE.
     * 
     * @return An unmodifiable set of interfaces for subscribed objects.
     */
    public Set<ObjectClassType> getSubscribedObjects() {
        return Collections.unmodifiableSet(subscribedObjects);
    }
    
    /**
     * Gets a set of attribute interfaces associated with the given object class. Although ObjectClassType defines a
     * method to retrieve its attributes, this method is not recursive and will not retrieve the attributes inherited
     * from parent object classes. This method will also return the inherited attributes.
     * 
     * @param object The object class whose attributes should be retrieved.
     * @return An unmodifiable set of attribute interfaces.
     */
    public Set<AttributeType> getAttributes(ObjectClassType object) {
        return Collections.unmodifiableSet(objectToAttributes.get(object));
    }
    
    /**
     * Gets the set of attributes associated with a given object that have a sharing of PUBLISH or PUBLISH_SUBSCRIBE.
     * 
     * @param object The object class whose attributes should be retrieved.
     * @return An unmodifiable set of published attributes.
     */
    public Set<AttributeType> getPublishedAttributes(ObjectClassType object) {
        Set<AttributeType> publishedAttributes = new HashSet<AttributeType>();
        for (AttributeType attribute : getAttributes(object)) {
            if (isPublish(attribute.getSharing())) {
                publishedAttributes.add(attribute);
            }
        }
        return publishedAttributes;
    }
    
    /**
     * Gets the set of attributes associated with a given object that have a sharing of SUBSCRIBE or PUBLISH_SUBSCRIBE.
     * 
     * @param object The object class whose attributes should be retrieved.
     * @return An unmodifiable set of subscribebd attributes.
     */
    public Set<AttributeType> getSubscribedAttributes(ObjectClassType object) {
        Set<AttributeType> subscribedAttributes = new HashSet<AttributeType>();
        for (AttributeType attribute : getAttributes(object)) {
            if (isSubscribe(attribute.getSharing())) {
                subscribedAttributes.add(attribute);
            }
        }
        return subscribedAttributes;
    }
    
    /**
     *  Get the model details for a specific object attribute using a string identifier.
     *  
     * @param object The object from which the attribute should be retrieved
     * @param attributeName The string identifier of the attribute to retrieve
     * @return The object model representation of the specified attribute
     */
    public AttributeType getAttribute(ObjectClassType object, String attributeName) {
        for (AttributeType attribute : getAttributes(object)) {
            if (attribute.getName().getValue().equals(attributeName)) {
                return attribute;
            }
        }
        return null;
    }
    
    private boolean isPublish(SharingType sharingType) {
        if (sharingType == null) {
            return false;
        }
        SharingEnumerations sharingValue = sharingType.getValue();
        if (sharingValue == SharingEnumerations.PUBLISH || sharingValue == SharingEnumerations.PUBLISH_SUBSCRIBE) {
            return true;
        }
        return false;
    }
    
    private boolean isSubscribe(SharingType sharingType) {
        if (sharingType == null) {
            return false;
        }
        SharingEnumerations sharingValue = sharingType.getValue();
        if (sharingValue == SharingEnumerations.SUBSCRIBE || sharingValue == SharingEnumerations.PUBLISH_SUBSCRIBE) {
            return true;
        }
        return false;
    }
    
    private void initializeInteractionVariables() {
        log.trace("initializeInteractionVariables");
        
        Queue<InteractionClassType> unprocessedInteractions = new LinkedList<InteractionClassType>();
        unprocessedInteractions.add(objectModel.getInteractions().getInteractionClass());
        
        while (!unprocessedInteractions.isEmpty()) {
            InteractionClassType nextInteraction = unprocessedInteractions.poll();
            String classPath = nextInteraction.getName().getValue();
            log.trace("on " + classPath);
            
            Set<String> knownParameters = new HashSet<String>();
            Set<ParameterType> parameters = new HashSet<ParameterType>();
            for (ParameterType parameter : nextInteraction.getParameter()) {
                String parameterName = parameter.getName().getValue();
                if (!knownParameters.add(parameterName)) {
                    log.warn("duplicate parameter " + parameterName);
                }
                parameters.add(parameter);
                log.trace("\twith parameter " + parameterName);
            }
            
            EObject parent = nextInteraction.eContainer();
            if (parent != null && parent instanceof InteractionClassType) {
                InteractionClassType parentInteraction = (InteractionClassType) parent;
                classPath = getClassPath(parentInteraction) + "." + classPath;
                log.trace("\tusing parent " + parentInteraction.getName().getValue());
                
                for (ParameterType parameter : getParameters(parentInteraction)) {
                    String parameterName = parameter.getName().getValue();
                    if (knownParameters.add(parameterName)) {
                        parameters.add(parameter);
                        log.trace("\twith inherited parameter " + parameterName);
                    }
                }
            }
            
            log.debug("parsed " + classPath + " with " + parameters.size() + " parameters");
            
            interactionToClassPath.put(nextInteraction, classPath);
            classPathToInteraction.put(classPath, nextInteraction);
            interactionToParameters.put(nextInteraction, parameters);
            
            if (isPublish(nextInteraction.getSharing())) {
                publishedInteractions.add(nextInteraction);
            }
            if (isSubscribe(nextInteraction.getSharing())) {
                subscribedInteractions.add(nextInteraction);
            }
            
            unprocessedInteractions.addAll(nextInteraction.getInteractionClass());
        }
    }
    
    // follow hla convention that sharing tag for object is based on its attributes
    private void initializeObjectVariables() {
        log.trace("initializeObjectVariables");
        
        Queue<ObjectClassType> unprocessedObjects = new LinkedList<ObjectClassType>();
        unprocessedObjects.add(objectModel.getObjects().getObjectClass());
        
        while (!unprocessedObjects.isEmpty()) {
            ObjectClassType nextObject = unprocessedObjects.poll();
            String classPath = nextObject.getName().getValue();
            log.trace("on " + classPath);
            
            Set<String> knownAttributes = new HashSet<String>();
            Set<AttributeType> attributes = new HashSet<AttributeType>();
            for (AttributeType attribute : nextObject.getAttribute()) {
                String attributeName = attribute.getName().getValue();
                if (!knownAttributes.add(attributeName)) {
                    log.warn("duplicate attribute " + attributeName);
                }
                attributes.add(attribute);
                log.trace("\twith attribute " + attributeName);
            }
            
            EObject parent = nextObject.eContainer();
            if (parent != null && parent instanceof ObjectClassType) {
                ObjectClassType parentObject = (ObjectClassType) parent;
                classPath = getClassPath(parentObject) + "." + classPath;
                log.trace("\tusing parent " + parentObject.getName().getValue());
                
                for (AttributeType attribute : getAttributes(parentObject)) {
                    String attributeName = attribute.getName().getValue();
                    if (knownAttributes.add(attributeName)) {
                        attributes.add(attribute);
                        log.trace("\twith inherited attribute " + attributeName);
                    }
                }
            }
            
            log.debug("parsed " + classPath + " with " + attributes.size() + " attributes");
            
            objectToClassPath.put(nextObject, classPath);
            classPathToObject.put(classPath, nextObject);
            objectToAttributes.put(nextObject, attributes);
            
            if (isPublish(nextObject.getSharing())) {
                publishedObjects.add(nextObject);
            }
            if (isSubscribe(nextObject.getSharing())) {
                subscribedObjects.add(nextObject);
            }
            
            unprocessedObjects.addAll(nextObject.getObjectClass());
        }
    }
}
