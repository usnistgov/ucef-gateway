package gov.nist.hla.ii.config;

public class InteractionInjectionConfig {

	private String federateName;
	private String federation;
	private String fomFile;
	private double lookahead;
	private double stepsize;
	
	public String getFederateName() {
		return federateName;
	}
	public void setFederateName(String federateName) {
		this.federateName = federateName;
	}
	public String getFederation() {
		return federation;
	}
	public void setFederation(String federation) {
		this.federation = federation;
	}
	public String getFomFile() {
		return fomFile;
	}
	public void setFomFile(String fomFile) {
		this.fomFile = fomFile;
	}
	public double getLookahead() {
		return lookahead;
	}
	public void setLookahead(double lookahead) {
		this.lookahead = lookahead;
	}
	public double getStepsize() {
		return stepsize;
	}
	public void setStepsize(double stepsize) {
		this.stepsize = stepsize;
	}
}
