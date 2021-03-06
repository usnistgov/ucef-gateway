package gov.nist.hla.gateway.exception;

public class UnsupportedServiceException extends RuntimeException {
    public UnsupportedServiceException(String message) {
        super(message);
    }

    public UnsupportedServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
