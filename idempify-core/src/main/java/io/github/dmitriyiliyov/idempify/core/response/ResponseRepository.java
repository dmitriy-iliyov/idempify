package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Optional;
import java.util.UUID;

/**
 * The response a completed operation answered with, read and written where it lives - on the operation's own
 * record.
 * <p>
 * This is not a store of its own: an implementation writes onto the row the operation already occupies and
 * reads back from that same row, so there is no second copy to go stale. It carries the replayable part
 * alone - the response, the fingerprint to check it against, and the expiry that bounds both - which is what
 * lets a repeat be answered without decoding a result nobody asked for.
 * <p>
 * This is also the seam a cache decorates: a lookup here is what an entry kept in front of the store stands
 * in for, and a miss falls through to the row.
 */
public interface ResponseRepository {

    /**
     * Writes the response onto the record of the operation already claimed under this key, and hands back the
     * record as it now stands.
     * <p>
     * The write is conditional on the operation having completed: a record still running, or one already
     * reclaimed by a later attempt, is not this response's own, and attaching the answer to it would let one
     * operation reply with another's. A missed condition is therefore a failure rather than an outcome to
     * inspect, as everywhere else in this store.
     *
     * @param idempotencyKey the key the operation was claimed under.
     * @param response       the answer to replay.
     * @return the record as it now stands - the response just written, the fingerprint it was claimed with,
     *         and the expiry that bounds how long it may be replayed.
     */
    RawResponseContainer save(UUID idempotencyKey, String response);

    Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey);
}
