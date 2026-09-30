package io.github.dmitriyiliyov.idempify.core;

/**
 * An {@link OperationDetail} of the non-blocking branch, carrying the claim it was produced under, so that
 * whatever the caller does next with the operation goes through the same token.
 */
public interface NonBlockingOperationDetail extends OperationDetail, LockDetail { }
