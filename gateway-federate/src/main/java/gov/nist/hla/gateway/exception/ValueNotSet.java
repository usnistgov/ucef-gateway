package gov.nist.hla.gateway.exception;

public class ValueNotSet extends RuntimeException {
    public ValueNotSet(String message) {
        super(message);
    }

    public ValueNotSet(String message, Throwable cause) {
        super(message, cause);
    }
}

