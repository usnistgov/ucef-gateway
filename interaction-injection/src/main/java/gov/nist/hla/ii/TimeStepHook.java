package gov.nist.hla.ii;

public interface TimeStepHook {

	void afterReadytoPopulate();

	void afterReadytoRun();

	void afterAdvanceLogicalTime();

	void beforeReadytoPopulate();

	void beforeReadytoRun();

	void beforeAdvanceLogicalTime();

}