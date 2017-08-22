# UCEF Gateway
Is a library that supports three broad capabilities:
1.  The sending of interactions without requiring that the WebGME generated interaction classes to be on the classpath.  
2.  The receiving of all interactions sent by other federates in the federation thereby enabling the user to dispoaition them at will.  
3.  The hooking into the fedration lifecycle at key points enabling the user to execute functionality outside the normal lifecycle.  

To employ this library, the user must implement, in a separate project called hereafter the "Implementation Project"., three of the library's interfaces and set them into the library's main execution routine.  A companion project: [UCEF Gateway Demo](https://github.com/usnistgov/ucef-gateway-demo.git) demonstrates such an implementation.  

The three capabilities:
  1. Injection
  The user implements this class to send interactions.  Interactions must be previously designed using WebGME but their implementations do not need to be on the gateway's classpath.  

  2. Reception
  The gateway subscribes to all interactions.  The user dispositions these interactions by implementing this class.  Reception also signals when a time step passes in which no interactions are received.  

  3. LifeCycle Hook
  Enables the user to execute fuctionality at key points during the processing life cycle.  

     Key points:  
     a. Before and after ready-to-populate  
     b. Before and after ready-to-run  
     c. Before and after each time step  

## Prerequisites
The UCEF Gateway cannot send interactios that are not designed by WebGME.  The gateway must have a complete set of the interactions in the form of a FOM (Federation Object Model) file.  A FOM is generated for any federate designed with WebGME.

## Configuration
Configuration is a properties file that must be located in the src/main/resources directory of the Implementation Project.  Usage is commented therein. 
