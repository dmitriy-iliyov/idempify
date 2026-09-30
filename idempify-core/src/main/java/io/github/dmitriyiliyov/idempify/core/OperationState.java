package io.github.dmitriyiliyov.idempify.core;

/**
 * What the core tells the transport about the operation it just ran, as far as anyone downstream needs to
 * know.
 * <p>
 * Its presence is half the signal: a transport that finds no state knows this request did not own the
 * operation - the core never ran for it, or ran it only as a duplicate that met another call's claim, and a
 * response of such a request is not the operation's to record.
 * The lifetime of anything derived from the operation is not here - that is read off the record itself, so
 * there is one place saying until when a stored answer may be replayed rather than two that can disagree.
 */
public interface OperationState {

    /**
     * Whether the transport should record the response this call is about to produce: {@code true} for an
     * operation this call has just run, {@code false} for one answered from the record - that answer is already
     * stored, so there is nothing new to write anywhere.
     */
    boolean shouldRecordResponse();
}
