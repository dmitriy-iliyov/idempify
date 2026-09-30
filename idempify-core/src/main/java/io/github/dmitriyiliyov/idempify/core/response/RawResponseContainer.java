package io.github.dmitriyiliyov.idempify.core.response;

/**
 * The replayable part of an operation's row, as the store holds it - the response still serialized, the
 * fingerprint to check it against, and how long the whole record stays replayable.
 * <p>
 * The remaining time is part of it because a cache in front of the store has to let its copy die together with
 * the record it stands for, and this is where it learns how long that is. It is a duration rather than an
 * instant so that nobody downstream compares the store's expiry against a clock of its own.
 */
public interface RawResponseContainer {

    /**
     * Returns the serialized response, or {@code null} when the operation is claimed but has not answered yet.
     */
    String getResponse();

    String getFingerprint();

    /**
     * Returns how long the record stays replayable, measured by the store's clock when it was read: zero or
     * negative once it has expired, {@code null} while the operation is still running - an expiry is written
     * only on completion.
     */
    Long getRemainingMillis();
}
