package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

/**
 * Reads a stored row back into an {@link Operation}.
 * <p>
 * The declared type of the result comes from the caller rather than from the row: it belongs to the call site,
 * and storing it beside the result would only duplicate what the caller already holds.
 *
 * @param <T> the row as the store holds it.
 * @param <K> the operation it decodes into.
 */
public interface OperationDeserializer<T, K> {

    /**
     * @param rawOperation the row as the store returned it, or {@code null}.
     * @param resultType   the type to read the value the method returned back into, or {@code null} for a caller
     *                     that does not want it - that value is then left unread, and one that cannot be read
     *                     back cannot fail the read either.
     * @return the decoded operation, or {@code null} when {@code rawOperation} is {@code null}.
     */
    K deserialize(T rawOperation, ResultType resultType);
}
