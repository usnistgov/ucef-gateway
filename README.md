# UCEF Gateway
A generic federate that supports three broad capabilities:
1. The sending of interactions and object updates without dependencies on WebGME generated Java classes.
2. The receiving of all interactions and object reflections sent by other federates in the federation.
3. The hooking into the federate life cycle to enable the user to execute custom functionality at key points.

The use of this library requires the user to create a concrete implementation of the GatewayCallback. Example implementations can be found in the test-federation directory of this project, as well as the ucef-samples companion github repository.

Additional documentation for the gateway can be found in the gateway-federate directory of this project.

