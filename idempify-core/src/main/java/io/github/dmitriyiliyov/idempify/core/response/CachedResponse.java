package io.github.dmitriyiliyov.idempify.core.response;

/**
 * A completed operation's answer, kept in a {@link ResponseCache} so a repeat of the same call can be replayed
 * without touching the repository or the business logic.
 */
public interface CachedResponse {

    int getStatus();

    byte [] getBody();

    String getContentType();

    /**
     * The fingerprint of the request this answer was produced for, or {@code null} if the operation did not
     * use one. It is what tells a genuine retry from the same key sent with a different request.
     */
    String getFingerprint();
}
