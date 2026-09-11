package io.github.dmitriyiliyov.idempify.core;

/**
 * What the core tells the transport about the operation it just ran, as far as anyone downstream needs to
 * know.
 * <p>
 * Its presence is half the signal: a transport that finds no state knows the core never ran for this request.
 * The lifetime of anything derived from the operation is not here - that is read off the record itself, so
 * there is one place saying until when a stored answer may be replayed rather than two that can disagree.
 */
public interface OperationState {

    /**
     * Whether the result was read back from the repository rather than produced by this call. A replay is
     * already stored, so there is nothing new to write anywhere.
     */
    boolean replayed();
}
