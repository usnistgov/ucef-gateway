The UCEF Gateway project is meant to be used as the basis for the implementation of an HLA federate. The gateway is designed to expose the minimal set of HLA features required to implement a federate that can translate between HLA messages and another communication protocol. It exposes three significant classes which are each documented using javadocs: InjectionFederate, InjectionCallback, and ObjectModel.

The gateway **does not support** the Save and Restore federation management services and the Ownership Management services. **It cannot join a federation that enables these services** as it will throw an exception.

# How to use the gateway

The gateway project does not have a main method and cannot be run; it is meant to be incorporated into a new maven project that implements a translation federate that converts HLA into another communication protocol. An example implementation called SensorAggregation can be found in the ucef-samples repository.

## Maven dependency

Bring the gateway into a new project using the following dependency:

    <groupId>gov.nist.hla</groupId>
    <artifactId>gateway-federate</artifactId>
    <version>1.0.0-SNAPSHOT</version>

## Integrate the gateway

Integrate the gateway into a new project with the following two steps:

1. Create a new instance of the `InjectionFederate` class
1. Execute the gateway code using the blocking `InjectionFederate::run` method

The `InjectionFederate` constructor requires a configuration file and an implementation of the `InjectionCallback` interface. Use the `InjectionFederate::readConfiguration(String)` static method to create the configuration file. More information on the callback interface is documented below.

**The gateway project is not thread safe**. Its behavior is undefined in a threaded environment.

## Configuration file

The configuration file must be JSON with the following fields:

- **federationId** The name of the HLA federation the gateway should join
- **federateName** A unique identifier for the gateway in the federation
- **fomFilepath** The path to the XML file that has the gateways publications and subscriptions
- **maxReconnectAttempts** How many times the gateway should try to join the federation before failure
- **waitReconnectMs** How long the gateway should wait between attempts to join the federation
- **isLateJoiner** A flag to indicate whether the gateway joins late (after initialization and synchronization)
- **stepSize** The gateway logical step size
- **lookAhead** A value less than the step size

See the SensorAggregation sample project for an example on how to extend the configuration class to incorporate additional fields for your project.

## Federation Object Model (FOM) file

The configuration file makes a reference to another file called the FOM. This file adheres to an XML schema specified in the HLA standard and an XML instance will have to be created for each configuration of your federate, often using WebGME code generation.

The most relevant fields in the FOM for the gateway are `<interactions>` and `<objects>` which contain all the known interaction and object classes, their parameters and attributes, information on data types, and publication and subscription interests. The gateway will use the `<sharing>` fields inside interactions and attributes to determine what to publish and subscribe to within the HLA federation. The sharing field for relevant interactions and attributes must be set to either *PUBLISH*, *SUBSCRIBE*, or *PUBLISH_SUBSCRIBE*.

# Main classes

Refer to the javadocs or the method comments for more information.

## InjectionFederate

The front end of the gateway project. It provides public methods to publish interactions and object updates, as well as the blocking run method which performs the full federate life cycle.

## InjectionCallback

An interface that defines a set of callback methods which will be invoked during the federate life cycle primarily concerned with receiving interactions and object updates. You must implement this interface and pass the implementation to the InjectionFederate constructor. Almost all implementations should maintain a reference to the InjectionFederate to enable the callbacks to invoke the publish data methods.

## ObjectModel

A set of methods to retrieve information from the FOM related to interactions and objects. These methods will return interfaces generated using the Eclipse Modeling Framework (EMF). The EMF interfaces are not documented; use the Eclipse IDE to see the accessors available for each interface, or refer to the sample projects to see how various queries can be executed.
