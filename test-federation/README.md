A simple test federation that tests whether the gateway federate can interact with a WebGME generated Java federate.

# Installation and Use
Compile the code:
1. Goto GatewayTest_generated and run `mvn install`
2. Goto GatewayTest_deployment and run `mvn install`
3. Goto GatewayFederate and run `mvn install` (after building the interaction-injection project in this repo)

Run the compiled code:
1. Goto GatewayTest_deployment and run `mvn exec:java -P FederationManagerExecJava`
2. Wait for the federation manager server to come online, then send the JSON message to start the simulation to the federation manager
3. Goto GatewayTest_deployment and run `mvn exec:java -P TestFederate,ExecJava`
4. Goto GatewayFederate and run `./run.sh`

The code will not terminate (due to the difference in timesteps between the two federates and bugs related to clean exits); it will freeze at around t=20.0 when the federation manager terminates itself. Update the federation manager configuration file to never terminate to avoid this behavior.
