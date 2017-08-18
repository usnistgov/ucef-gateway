package gov.nist.hla.ii;

public abstract class TimeStepHookImpl implements TimeStepHook {

	@Override
	public void afterReadytoPopulate() {}

	@Override
	public void afterReadytoRun() {}

	@Override
	public void afterAdvanceLogicalTime() {}

	@Override
	public void beforeReadytoPopulate() {}

	@Override
	public void beforeReadytoRun() {}

	@Override
	public void beforeAdvanceLogicalTime() {}
}
