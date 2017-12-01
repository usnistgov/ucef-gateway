package gov.nist.hla.ii.config;

import gov.nist.hla.ii.exception.ValueNotSet;

public class InjectionFederateConfig {
    private String federateName = "InjectionFederate";
    
    private String federationId;
    private boolean federationIdSet = false;
    
    private String fomFilepath;
    private boolean fomFilepathSet = false;
    
    private int maxReconnectAttempts = 5;
    
    private long waitReconnectMs = 5000;
    
    private boolean isLateJoiner = false;
    
    private double lookAhead = 1.0;
    
    private double stepSize = 0.1;
    
    public void setFederateName(String federateName) {
        this.federateName = federateName;
    }
    
    public String getFederateName() {
        return federateName;
    }

    public void setFederationId(String federationId) {
        this.federationId = federationId;
        this.federationIdSet = true;
    }
    
    public String getFederationId() {
        if (!federationIdSet) {
            throw new ValueNotSet("federationId");
        }
        return federationId;
    }

    public void setFomFilepath(String fomFilepath) {
        this.fomFilepath = fomFilepath;
        this.fomFilepathSet = true;
    }

    public String getFomFilepath() {
        if (!fomFilepathSet) {
            throw new ValueNotSet("fomFilepath");
        }
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
