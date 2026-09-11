package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Instant;

/**
 * The replayable part of an operation's row, as the store holds it - the response still serialized, the
 * fingerprint to check it against, and the moment the whole record stops being replayable.
 * <p>
 * The expiry is part of it because a cache in front of the store has to let its copy die together with the
 * record it stands for, and this is where it learns when that is.
 */
public interface RawResponseContainer {

    /**
     * Returns the serialized response, or {@code null} when the operation is claimed but has not answered yet.
     */
    String getResponse();

    String getFingerprint();

    /**
     * Returns when the record stops being replayable, or {@code null} while the operation is still running -
     * an expiry is written only on completion.
     */
    Instant getExpiresAt();
}
