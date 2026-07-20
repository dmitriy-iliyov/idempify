package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.UUID;

public record DefaultFingerprintMismatchContext(
        UUID idempotencyKey,
        String previousFingerprint,
        String currentFingerprint
) implements FingerprintMismatchContext {

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public String getPreviousFingerprint() {
        return previousFingerprint;
    }

    @Override
    public String getCurrentFingerprint() {
        return currentFingerprint;
    }
}
