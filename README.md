This code was designed to run in the UCEF Virtual Machine (https://github.com/usnistgov/ucef). When using a different environment, the Eclipse Modeling Framework dependencies will have to be extracted from Eclipse and installed into the local maven repository before the code will compile. The gateway has very limited support when run outside of UCEF.

# UCEF Gateway
A generic federate that supports three broad capabilities:
1. The sending of interactions and object updates without dependencies on WebGME generated Java classes.
2. The receiving of all interactions and object reflections sent by other federates in the federation.
3. The hooking into the federate life cycle to enable the user to execute custom functionality at key points.

The use of this library requires the user to create a concrete implementation of the GatewayCallback. Refer to the ucef-samples repository for an example implementation called SensorAggregation.

Additional documentation for the gateway can be found in the gateway-federate directory of this project.
