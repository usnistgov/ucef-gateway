package gov.nist.hla.ii.exception;

public class ValueNotSet extends RuntimeException {
    public ValueNotSet(String message) {
        super(message);
    }

    public ValueNotSet(String message, Throwable cause) {
        super(message, cause);
    }
}

