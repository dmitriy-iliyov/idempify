package io.github.dmitriyiliyov.springidempotency.core;

import java.util.Optional;
import java.util.UUID;

public interface OperationRepository {
    Operation saveIfAbsent(Operation operation);

    void saveResponseAndUpdateState(String response, OperationState oldState, OperationState newState);

    Optional<Operation> findByIdempotencyKey(UUID idempotencyKey);
}
