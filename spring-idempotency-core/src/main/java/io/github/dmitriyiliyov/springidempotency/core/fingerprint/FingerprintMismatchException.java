package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

public class FingerprintMismatchException extends RuntimeException {

    private final FingerprintMismatchContext context;

    public FingerprintMismatchException(FingerprintMismatchContext context, String message) {
        super(message);
        this.context = context;
    }

    public FingerprintMismatchException(FingerprintMismatchContext context) {
        this.context = context;
    }

    public FingerprintMismatchContext getContext() {
        return context;
    }
}
