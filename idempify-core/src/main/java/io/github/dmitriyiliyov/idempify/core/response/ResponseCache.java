package io.github.dmitriyiliyov.idempify.core.response;

import java.time.Duration;
import java.util.UUID;

/**
 * Secondary store for the responses of completed operations, sitting in front of the
 * {@code OperationRepository}.
 * <p>
 * The repository stays the source of truth, so a miss here is not an error - it only means the reply is read
 * from the database instead. An implementation is free to evict an entry at any time.
 */
public interface ResponseCache {

    /**
     * Returns the cached response for the key, or {@code null} on a miss.
     */
    CachedResponse findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Stores the response for the key.
     *
     * @param idempotencyKey the key the response is replayed by.
     * @param response       the answer to replay.
     * @param ttl            how much of the operation's own lifetime is left; the entry must not outlive it.
     */
    void save(UUID idempotencyKey, CachedResponse response, Duration ttl);
}
