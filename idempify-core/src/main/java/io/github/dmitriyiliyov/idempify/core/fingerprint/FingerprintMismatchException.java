package io.github.dmitriyiliyov.idempify.core.fingerprint;

public class FingerprintMismatchException extends RuntimeException {

    private final FingerprintMismatchContext context;

    public FingerprintMismatchException(FingerprintMismatchContext context, String message) {
        super(message);
        this.context = context;
    }

    public FingerprintMismatchException(FingerprintMismatchContext context) {
        super("Operation (idempotencyKey=%s) was retried with a different request fingerprint"
                .formatted(context.getIdempotencyKey()));
        this.context = context;
    }

    public FingerprintMismatchContext getContext() {
        return context;
    }
}
