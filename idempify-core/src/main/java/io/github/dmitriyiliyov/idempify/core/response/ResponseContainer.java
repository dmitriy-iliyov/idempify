package io.github.dmitriyiliyov.idempify.core.response;

/**
 * The response a replay is answered with, together with the fingerprint the operation was claimed under.
 * <p>
 * The two travel together because a replay may not happen without checking them against each other: the
 * fingerprint is what tells a genuine repeat from a different request reusing the key.
 */
public interface ResponseContainer {
    Response getResponse();
    String getFingerprint();
}
