package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Carries what the repository recorded about an operation from the code that wrote it to the code that
 * answers the client. Scoped to one call: an implementation must never let one request see another's state.
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
