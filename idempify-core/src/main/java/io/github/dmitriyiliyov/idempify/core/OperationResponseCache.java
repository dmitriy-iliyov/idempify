package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

public interface OperationResponseCache {
    void save(UUID idempotencyKey, Object operationResult);
    Object findByIdempotencyKey(UUID idempotencyKey);
}
