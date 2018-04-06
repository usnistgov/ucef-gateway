package gov.nist.hla.gateway;

import java.util.Map;

/**
 * This interface defines the set of callback methods that the {@link GatewayFederate} will invoke during the course
 * of its life cycle. These methods will be invoked during the call to {@link GatewayFederate#run}, and it is safe
 * and intended for all of these methods to use the public members of {@link GatewayFederate}.
 * 
 * @author Thomas Roth
 */
public interface GatewayCallback {    
    /**
     * This callback should be used to perform initialization functions that depend on information about a joined
     * federation. It is called after the {@link GatewayFederate} joins the federation, but before the the
     * federation has full membership. It will be called exactly once per {@link GatewayFederate#run}, and no
     * other callbacks will be called before it.
     */
    void initializeSelf();

    /**
     * This callback should be used to perform initialization functions that depend on other members of the federation.
     * It is called after all expected federates have joined the federation, but before the {@link GatewayFederate}
     * starts logical time progression. It will be called exactly once per {@link GatewayFederate#run}.
     */
    void initializeWithPeers();

    /**
     * This callback is invoked once per received interaction. All interactions sent with a timestamp will be received
     * during each logical time step before the call to {@link #doTimeStep}. This callback will also be invoked during
     * explicit calls to {@link GatewayFederate#tick} when polling for receive order interactions.
     * <p>
     * A federate does not receive its own published interactions.
     * <p>
     * It is not possible in this callback to determine whether the interaction was sent using a timestamp.
     * <p>
     * The value of {@code timeStep} is not the current logical time granted to the federate by the federation, but
     * rather the last logical time used for an advance time request. These two values will be identical during the
     * {@link #doTimeStep} callback, but {@code timeStep} will otherwise be equal to the next (requested) time step.
     * This detail should be irrelevant for most implementations.
     * 
     * @param timeStep The last requested logical time
     * @param className The HLA class name of the received interaction
     * @param parameters A map of parameter names to their received values
     */
    void receiveInteraction(Double timeStep, String className, Map<String, String> parameters);

    /**
     * This callback is invoked once per received object reflection. All object updates sent with a timestamp will be
     * received during each logical time step before the call to {@link #doTimeStep}. This callback can also be invoked
     * during explicit calls to {@link GatewayFederate#tick} when polling for receive order object updates.
     * <p>
     * The attributes passed in as arguments will be the subset of the object's attributes whose values have changed
     * since the last callback. The user application is responsible for maintaining the complete object state.
     * <p>
     * A federate does not receive its own published object updates.
     * <p>
     * It is not possible in this callback to determine whether an object update was sent using a timestamp.
     * <p>
     * The value of {@code timeStep} is not the current logical time granted to the federate by the federation, but
     * rather the last logical time used for an advance time request. These two values will be identical during the
     * {@link #doTimeStep} callback, but {@code timeStep} will otherwise be equal to the next (requested) time step.
     * This detail should be irrelevant for most implementations.
     * 
     * @param timeStep The last requested logical time
     * @param className The HLA class name of the updated object
     * @param instanceName The unique instance name of the updated object
     * @param attributes A map of attribute names to their received values
     */
    void receiveObject(Double timeStep, String className, String instanceName, Map<String, String> attributes);
    
    /**
     * This callback should be used to perform functions that must occur during each logical time step. It is called
     * immediately before the logical time advance request to HLA, and occurs after all interactions and object
     * updates have been received. It is called exactly once per logical time step.
     * 
     * @param timeStep The current logical time
     */
    void doTimeStep(Double timeStep);
    
    /**
     * This callback should be used to perform cleanup functions for the user application. It is called after the
     * {@link GatewayFederate} resigns from the federation, but before {@link GatewayFederate#run} returns. It
     * will be called exactly once per {@link GatewayFederate#run}, and no other callbacks will be called after it.
     */
    void terminate();
}
