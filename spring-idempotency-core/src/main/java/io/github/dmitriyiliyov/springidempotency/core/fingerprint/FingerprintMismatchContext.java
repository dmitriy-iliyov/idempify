package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

import java.util.UUID;

public interface FingerprintMismatchContext {
    UUID getIdempotencyKey();
    String getPreviousFingerprint();
    String getCurrentFingerprint();
}
