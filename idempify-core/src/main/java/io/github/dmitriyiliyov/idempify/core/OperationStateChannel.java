package io.github.dmitriyiliyov.idempify.core;

/**
 * Carries what the repository recorded about an operation from the code that wrote it to the code that
 * answers the client. Scoped to one call: an implementation must never let one request see another's state.
 * <p>
 * What is announced belongs to the operation of the current call, so a reader may attribute it to the
 * idempotency key it computed itself. That rests on where {@link Idempotent} sits - on an entry into the
 * system, so one call carries one operation and there is one state to announce - and on both entries taking
 * the key from the metadata of one {@link OperationMetadataResolver}, so they cannot arrive at two different
 * keys for the same request.
 */
public interface OperationStateChannel {

    /**
     * Announces the record the repository now holds. Publishing again replaces what was announced before.
     */
    void publish(OperationState state);

    /**
     * Takes the announced state, or {@code null} if nothing was announced for this call - which is the normal
     * answer when the operation never reached the repository. Reading it consumes it.
     */
    OperationState consume();
}
