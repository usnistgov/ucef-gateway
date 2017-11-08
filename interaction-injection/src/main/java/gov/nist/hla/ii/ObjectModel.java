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
    
    public ObjectModel(String filepath) {
        this.objectModel = ObjectModel.readObjectModel(filepath);
        initializeInteractionVariables();
        initializeObjectVariables();
    }
    
    public String getClassPath(InteractionClassType interaction) {
        return interactionToClassPath.get(interaction);
    }
    
    public InteractionClassType getInteraction(String classPath) {
        return classPathToInteraction.get(classPath);
    }
    
    public Set<InteractionClassType> getPublishedInteractions() {
        return Collections.unmodifiableSet(publishedInteractions);
    }
    
    public Set<InteractionClassType> getSubscribedInteractions() {
        return Collections.unmodifiableSet(subscribedInteractions);
    }
    
    public Set<ParameterType> getParameters(InteractionClassType interaction) {
        return Collections.unmodifiableSet(interactionToParameters.get(interaction));
    }
    
    public ParameterType getParameter(InteractionClassType interaction, String parameterName) {
        for (ParameterType parameter : getParameters(interaction)) {
            if (parameter.getName().getValue().equals(parameterName)) {
                return parameter;
            }
        }
        return null;
    }
    
    public String getClassPath(ObjectClassType object) {
        return objectToClassPath.get(object);
    }
    
    public ObjectClassType getObject(String classPath) {
        return classPathToObject.get(classPath);
    }
    
    public Set<ObjectClassType> getPublishedObjects() {
        return Collections.unmodifiableSet(publishedObjects);
    }
    
    public Set<ObjectClassType> getSubscribedObjects() {
        return Collections.unmodifiableSet(subscribedObjects);
    }
    
    public Set<AttributeType> getAttributes(ObjectClassType object) {
        return Collections.unmodifiableSet(objectToAttributes.get(object));
    }
    
    public Set<AttributeType> getPublishedAttributes(ObjectClassType object) {
        Set<AttributeType> publishedAttributes = new HashSet<AttributeType>();
        for (AttributeType attribute : getAttributes(object)) {
            if (isPublish(attribute.getSharing())) {
                publishedAttributes.add(attribute);
            }
        }
        return publishedAttributes;
    }
    
    public Set<AttributeType> getSubscribedAttributes(ObjectClassType object) {
        Set<AttributeType> subscribedAttributes = new HashSet<AttributeType>();
        for (AttributeType attribute : getAttributes(object)) {
            if (isSubscribe(attribute.getSharing())) {
                subscribedAttributes.add(attribute);
            }
        }
        return subscribedAttributes;
    }
    
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
        log.trace("parsing interaction classes");
        
        Queue<InteractionClassType> unprocessedInteractions = new LinkedList<InteractionClassType>();
        unprocessedInteractions.add(objectModel.getInteractions().getInteractionClass());
        
        while (!unprocessedInteractions.isEmpty()) {
            InteractionClassType nextInteraction = unprocessedInteractions.poll();
            String classPath = nextInteraction.getName().getValue();
            log.trace("on " + classPath);
            
            Set<ParameterType> parameters = new HashSet<ParameterType>();
            parameters.addAll(nextInteraction.getParameter());
            
            EObject parent = nextInteraction.eContainer();
            if (parent != null && parent instanceof InteractionClassType) {
                InteractionClassType parentInteraction = (InteractionClassType) parent;
                log.trace("using parent " + parentInteraction.getName().getValue());
                classPath = getClassPath(parentInteraction) + "." + classPath;
                parameters.addAll(getParameters(parentInteraction));
            }
            
            log.debug("new interaction " + classPath + " " + Arrays.toString(parameters.toArray()));
            
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
        log.trace("parsing object classes");
        
        Queue<ObjectClassType> unprocessedObjects = new LinkedList<ObjectClassType>();
        unprocessedObjects.add(objectModel.getObjects().getObjectClass());
        
        while (!unprocessedObjects.isEmpty()) {
            ObjectClassType nextObject = unprocessedObjects.poll();
            String classPath = nextObject.getName().getValue();
            log.trace("on " + classPath);
            
            Set<AttributeType> attributes = new HashSet<AttributeType>();
            attributes.addAll(nextObject.getAttribute());
            
            EObject parent = nextObject.eContainer();
            if (parent != null && parent instanceof ObjectClassType) {
                ObjectClassType parentObject = (ObjectClassType) parent;
                log.trace("using parent " + parentObject.getName().getValue());
                classPath = getClassPath(parentObject) + "." + classPath;
                attributes.addAll(getAttributes(parentObject));
            }
            
            log.debug("new object " + classPath + " " + Arrays.toString(attributes.toArray()));
            
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
