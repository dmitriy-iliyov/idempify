package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.OperationState;

import java.util.UUID;

/**
 * What one pass through the manager learned about an operation: which key it ran under, what state the store
 * holds it in, and the result to hand back.
 * <p>
 * It stands where an {@code Optional} result used to: emptiness cannot carry that answer, because a business
 * method is allowed to return {@code null} and an absent value would then be indistinguishable from a stored
 * one. Here the status carries it and the result carries nothing but itself.
 */
public interface OperationDetail<T> extends OperationState {

    UUID getIdempotencyKey();

    /**
     * {@link OperationStatus#PROCESSED} when the store already holds a finished operation under this key -
     * that is the whole test for whether the business method still has to run.
     */
    OperationStatus getStatus();

    /**
     * The result of the operation - replayed from the store or just produced, depending on how this detail
     * was obtained. {@code null} is a legal result and never means "no result"; the status answers that.
     */
    T getResult();
}
