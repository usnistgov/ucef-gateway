package gov.nist.hla.ii;

import java.util.Map;

/**
 * This interface defines the set of callback methods that the {@link InjectionFederate} will invoke during the course
 * of its life cycle. These methods will be invoked during the call to {@link InjectionFederate#run}, and it is safe
 * and intended for all of these methods to use the public members of {@link InjectionFederate}.
 * 
 * @author Thomas Roth
 */
public interface InjectionCallback {
    /**
     * This callback is invoked once per received interaction. It will be called during each logical time step between
     * {@link #beforeTimeStep} and {@link #afterTimeStep}. It will also be called for receive order interactions during
     * explicit calls from the concrete implementation of this interface to {@link InjectionFederate#tick}.
     * <p>
     * A federate does not receive its own sent interactions. It is not possible to determine whether a received
     * interaction is receive order or not from this callback.
     * 
     * @param timeStep The current logical time
     * @param className The HLA class name of the received interaction
     * @param parameters A map of parameter names to their current values
     */
    void receiveInteraction(Double timeStep, String className, Map<String, String> parameters);

    /**
     * This callback is invoked once per received object reflection. It will be called during each logical time step
     * between {@link #beforeTimeStep} and {@link #afterTimeStep}. It will also be called for receive order updates
     * during explicit calls from the concrete implementation of this interface to {@link InjectionFederate#tick}.
     * <p>
     * The received attributes will be the subset of the object's attributes that have been updated. A federate does
     * not receive its own object updates. It is not possible to determine whether an object update was sent using
     * receive order or not from this callback.
     * 
     * @param timeStep The current logical time
     * @param className The HLA class name of the updated object
     * @param instanceName The unique instance name of the updated object
     * @param attributes A map of attribute names to their current values
     */
    void receiveObject(Double timeStep, String className, String instanceName, Map<String, String> attributes);
    
    /**
     * This callback should be used to perform initialization functions that depend on information about a joined
     * federation. It is called after the {@link InjectionFederate} joins the federation, but before the the
     * federation has full membership. It will be called exactly once per {@link InjectionFederate#run}, and no
     * other callbacks will be called before it.
     */
    void initializeSelf();

    /**
     * This callback should be used to perform initialization functions that depend on other members of the federation.
     * It is called after all expected federates have joined the federation, but before the {@link InjectionFederate}
     * starts logical time progression. It will be called exactly once per {@link InjectionFederate#run}.
     */
    void initializeWithPeers();

    /**
     * This callback should be used to perform functions that must occur at the start of a logical time step. It is
     * called immediately after the logical time grant from HLA, and occurs before interactions and object updates are
     * received. It is called exactly once per logical time step.
     * 
     * @param timeStep The current logical time
     */
    void beforeTimeStep(Double timeStep);

    /**
     * This callback should be used to perform functions that must occur at the end of a logical time step. It is
     * called immediately before the logical time advance request to HLA, and occurs after all interactions and object
     * updates have been received. It is called exactly once per logical time step.
     * 
     * @param timeStep The current logical time
     */
    void afterTimeStep(Double timeStep);
    
    /**
     * This callback should be used to perform cleanup functions for the user application. It is called after the
     * {@link InjectionFederate} resigns from the federation, but before {@link InjectionFederate#run} returns. It
     * will be called exactly once per {@link InjectionFederate#run}, and no other callbacks will be called after it.
     */
    void afterResignation();
}
