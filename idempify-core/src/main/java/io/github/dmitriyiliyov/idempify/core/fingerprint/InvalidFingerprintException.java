package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

public class InvalidFingerprintException extends RuntimeException {

    private final UUID idempotencyKey;

    public InvalidFingerprintException(UUID idempotencyKey) {
        super("Generated fingerprint for operation (idempotencyKey=%s) is null or blank".formatted(idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
