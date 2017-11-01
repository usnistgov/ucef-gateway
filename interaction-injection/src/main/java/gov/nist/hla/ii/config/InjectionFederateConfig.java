package gov.nist.hla.ii.config;

public class InjectionFederateConfig {
    private String federateName;
    private String federationId;
    private String fomFilepath;
    private int maxReconnectAttempts;
    private long waitReconnectMs;
    private boolean isLateJoiner;
    private double lookAhead;
    private double stepSize;
    
    public void setFederateName(String federateName) {
        this.federateName = federateName;
    }
    
    public String getFederateName() {
        return federateName;
    }

    public void setFederationId(String federationId) {
        this.federationId = federationId;
    }
    
    public String getFederationId() {
        return federationId;
    }

    public void setFomFilepath(String fomFilepath) {
        this.fomFilepath = fomFilepath;
    }

    public String getFomFilepath() {
        return fomFilepath;
    }
    
    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }
    
    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }
    
    public void setWaitReconnectMs(long waitReconnectMs) {
        this.waitReconnectMs = waitReconnectMs;
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
    
    public void setLookAhead(double lookAhead) {
        this.lookAhead = lookAhead;
    }
    
    public double getLookAhead() {
        return lookAhead;
    }

    public void setStepSize(double stepSize) {
        this.stepSize = stepSize;
    }

    public double getStepSize() {
        return stepSize;
    }
}
