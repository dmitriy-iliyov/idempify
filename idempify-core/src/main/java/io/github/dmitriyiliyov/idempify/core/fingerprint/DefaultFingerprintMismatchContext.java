package io.github.dmitriyiliyov.idempify.core.fingerprint;

import java.util.Objects;
import java.util.UUID;

public final class DefaultFingerprintMismatchContext implements FingerprintMismatchContext {

    private final UUID idempotencyKey;
    private final String previousFingerprint;
    private final String currentFingerprint;

    public DefaultFingerprintMismatchContext(UUID idempotencyKey,
                                             String previousFingerprint,
                                             String currentFingerprint) {
        this.idempotencyKey = idempotencyKey;
        this.previousFingerprint = previousFingerprint;
        this.currentFingerprint = currentFingerprint;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultFingerprintMismatchContext that)) return false;
        return Objects.equals(idempotencyKey, that.idempotencyKey)
                && Objects.equals(previousFingerprint, that.previousFingerprint)
                && Objects.equals(currentFingerprint, that.currentFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, previousFingerprint, currentFingerprint);
    }

    @Override
    public String toString() {
        return "DefaultFingerprintMismatchContext{" +
                "idempotencyKey=" + idempotencyKey +
                ", previousFingerprint='" + previousFingerprint + '\'' +
                ", currentFingerprint='" + currentFingerprint + '\'' +
                '}';
    }
}
