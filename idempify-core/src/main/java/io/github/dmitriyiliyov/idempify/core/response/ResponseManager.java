package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Optional;
import java.util.UUID;

/**
 * Puts the response of a completed operation onto its record and reads it back for a replay, keeping the
 * serialization the record needs away from the transport that produces and consumes the response.
 * <p>
 * An empty lookup covers a key nothing was ever claimed under, a record that carries no response yet and a
 * response past its expiry - a caller that has to tell those apart cannot do it here.
 */
public interface ResponseManager {

    void save(UUID idempotencyKey, Response response);

    Optional<ResponseContainer> findByIdempotencyKey(UUID idempotencyKey);
}
