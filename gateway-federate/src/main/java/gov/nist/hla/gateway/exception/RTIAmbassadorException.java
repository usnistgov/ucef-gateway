package gov.nist.hla.gateway.exception;

public class RTIAmbassadorException extends RuntimeException {
    public RTIAmbassadorException(String message, Throwable cause) {
        super(message, cause);
    }

    public RTIAmbassadorException(Throwable cause) {
        super(cause);
    }
}
