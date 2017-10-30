package gov.nist.hla.ii.config;

public class InjectionFederateConfig {

    private String federateName;
    private String federation;
    private String fomFile;
    private double lookahead;
    private double stepsize;
    private int maxReconnectAttempts;
    private long waitReconnectMs;
    private boolean isLateJoiner;

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
    
    public void setMaxReconnectAttempts(int max) {
        this.maxReconnectAttempts = max;
    }
    
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }
    
    public void setWaitReconnectMs(long ms) {
        this.waitReconnectMs = ms;
    }
    
    public long getWaitReconnectMs() {
        return waitReconnectMs;
    }
    
    public void setIsLateJoiner(boolean isLateJoiner) {
        this.isLateJoiner = isLateJoiner;
    }
    
    public boolean getIsLateJoiner() {
        return isLateJoiner;
    }
}
