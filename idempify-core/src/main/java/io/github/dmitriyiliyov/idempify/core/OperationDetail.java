package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * What one pass through the manager learned about an operation: which key it ran under, what state the store
 * holds it in, and the result to hand back.
 * <p>
 * The status is what says whether the business method still has to run. The result says nothing but itself,
 * {@code null} included, because a business method is allowed to return none.
 */
public interface OperationDetail extends OperationState {

    UUID getIdempotencyKey();

    OperationStatus getStatus();

    Object getResult();
}
