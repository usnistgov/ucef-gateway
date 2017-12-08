package GatewayTest;

import org.cpswt.config.FederateConfig;
import org.cpswt.config.FederateConfigParser;
import org.cpswt.hla.base.ObjectReflector;
import org.cpswt.hla.ObjectRoot;
import org.cpswt.hla.InteractionRoot;
import org.cpswt.hla.base.AdvanceTimeRequest;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The TestFederate type of federate for the federation designed in WebGME.
 *
 */
public class TestFederate extends TestFederateBase {

    private final static Logger log = LogManager.getLogger(TestFederate.class);

    private static final String VALID_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    
    double currentTime = 0;
    
    TestObject testObjectInstance = new TestObject();
    
    boolean booleanValue;
    
    double doubleValue;
    
    int intValue;
    
    String stringValue;

    public TestFederate(FederateConfig params) throws Exception {
        super(params);
        testObjectInstance.registerObject(getLRC());
    }

    private void CheckReceivedSubscriptions(String s) {

        InteractionRoot interaction = null;
        while ((interaction = getNextInteractionNoWait()) != null) {
            if (interaction instanceof TestInteraction) {
                handleInteractionClass((TestInteraction) interaction);
            }
        }
 
        ObjectReflector reflector = null;
        while ((reflector = getNextObjectReflectorNoWait()) != null) {
            reflector.reflect();
            ObjectRoot object = reflector.getObjectRoot();
            if (object instanceof TestObject) {
                handleObjectClass((TestObject) object);
            }
        }
    }

    private void execute() throws Exception {
        if(super.isLateJoiner()) {
            currentTime = super.getLBTS() - super.getLookAhead();
            super.disableTimeRegulation();
        }

        AdvanceTimeRequest atr = new AdvanceTimeRequest(currentTime);
        putAdvanceTimeRequest(atr);
        
        // check if parameter handles match for child and parent interactions
        int interactionRoot = getLRC().getInteractionClassHandle("InteractionRoot.C2WInteractionRoot");
        int federateJoin = getLRC().getInteractionClassHandle(
                "InteractionRoot.C2WInteractionRoot.FederateJoinInteraction");
        int sourceFedR = getLRC().getParameterHandle("sourceFed", interactionRoot);
        int sourceFedJ = getLRC().getParameterHandle("sourceFed", federateJoin);
        log.debug(String.format("C2WInteractionRoot.sourceFed=%d : FederateJoin.sourceFed=%d", sourceFedR, sourceFedJ));

        if(!super.isLateJoiner()) {
            readyToPopulate();
        }

        if(!super.isLateJoiner()) {
            readyToRun();
        }

        startAdvanceTimeThread();

        // this is the exit condition of the following while loop
        // it is used to break the loop so that latejoiner federates can
        // notify the federation manager that they left the federation
        boolean exitCondition = false;
        
        while (true) {
            atr.requestSyncStart();
            enteredTimeGrantedState();
            log.info("t = " + currentTime);
            
            CheckReceivedSubscriptions("Main Loop");
            
            booleanValue = ThreadLocalRandom.current().nextBoolean();
            doubleValue = ThreadLocalRandom.current().nextDouble(1000);
            intValue = ThreadLocalRandom.current().nextInt(10000);
            stringValue = generateStringValue();
            
            TestInteraction testInteraction = create_TestInteraction();
            testInteraction.set_booleanValue(booleanValue);
            testInteraction.set_doubleValue(doubleValue);
            testInteraction.set_intValue(intValue);
            testInteraction.set_stringValue(stringValue);
            testInteraction.sendInteraction(getLRC(), currentTime + super.getLookAhead());
            log.info("sent " + testInteraction.toString());
            
            testObjectInstance.set_booleanValue(booleanValue);
            testObjectInstance.set_doubleValue(doubleValue);
            testObjectInstance.set_intValue(intValue);
            testObjectInstance.set_stringValue(stringValue);
            testObjectInstance.updateAttributeValues(getLRC(), currentTime + super.getLookAhead());
            log.info("sent " + testObjectInstance.toString());

            currentTime += super.getStepSize();
            AdvanceTimeRequest newATR = new AdvanceTimeRequest(currentTime);
            putAdvanceTimeRequest(newATR);
            atr.requestSyncEnd();
            atr = newATR;

            if(exitCondition) {
                break;
            }
        }

        // while loop finished, notify FederationManager about resign
        super.notifyFederationOfResign();
    }

    private void handleInteractionClass(TestInteraction interaction) {
        log.info("received TestInteraction");
        checkBooleanValue(interaction.get_booleanValue());
        checkDoubleValue(interaction.get_doubleValue());
        checkIntValue(interaction.get_intValue());
        checkStringValue(interaction.get_stringValue());
        
        // see what happens to the value when the parameter handles don't match
        log.debug("sourceFed received as " + interaction.get_sourceFed());
    }

    private void handleObjectClass(TestObject object) {
        log.info("received TestObject reflection");
        checkBooleanValue(object.get_booleanValue());
        checkDoubleValue(object.get_doubleValue());
        checkIntValue(object.get_intValue());
        checkStringValue(object.get_stringValue());
    }
    
    private String generateStringValue() {
        StringBuffer buffer = new StringBuffer(64);
        for (int i = 0; i < 64; i++) {
            buffer.append(VALID_CHARACTERS.charAt(ThreadLocalRandom.current().nextInt(VALID_CHARACTERS.length())));
        }
        return buffer.toString();
    }
    
    private void checkBooleanValue(boolean value) {
        log.info("\treceived " + value);
        log.info("\texpected " + booleanValue);
        if (value != booleanValue) {
            log.error("FAILED - boolean value incorrect");
            //throw new RuntimeException("boolean value incorrect");
        }
    }
    
    private void checkDoubleValue(double value) {
        log.info("\treceived " + value);
        log.info("\texpected " + doubleValue);
        if (value != doubleValue) {
            log.error("FAILED - double value incorrect");
            //throw new RuntimeException("double value incorrect");
        }
    }
    
    private void checkIntValue(int value) {
        log.info("\treceived " + value);
        log.info("\texpected " + intValue);
        if (value != intValue) {
            log.error("FAILED - int value incorrect");
            //throw new RuntimeException("int value incorrect");
        }
    }

    private void checkStringValue(String value) {
        log.info(String.format("\treceived %s (%d)", value, value.length()));
        log.info(String.format("\texpected %s (%d)", stringValue, stringValue.length()));
        if (!value.equals(stringValue)) {
            log.error("FAILED - string value incorrect");
            //throw new RuntimeException("string value incorrect");
        }
    }

    public static void main(String[] args) {
        try {
            FederateConfigParser federateConfigParser = new FederateConfigParser();
            FederateConfig federateConfig = federateConfigParser.parseArgs(args, FederateConfig.class);
            TestFederate federate = new TestFederate(federateConfig);
            federate.execute();

            System.exit(0);
        } catch (Exception e) {
            log.error("There was a problem executing the TestFederate federate: {}", e.getMessage());
            log.error(e);

            System.exit(1);
        }
    }
}
