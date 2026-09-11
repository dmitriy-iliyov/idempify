package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.util.UUID;

/**
 * What a {@link ConflictHandler} is told about the conflict it has to resolve: which key is contended, and
 * what type the result must have if the handler produces one.
 */
public interface ConflictContext {

    UUID getIdempotencyKey();

    ResultType getOperationResultType();
}
