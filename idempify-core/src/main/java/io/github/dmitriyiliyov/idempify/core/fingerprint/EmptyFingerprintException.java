package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

public class EmptyFingerprintException extends FingerprintException {

    private final UUID idempotencyKey;

    public EmptyFingerprintException(UUID idempotencyKey) {
        super("Generated fingerprint for operation (idempotencyKey=%s) is null empty or blank".formatted(idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
