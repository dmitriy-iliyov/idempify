package io.github.dmitriyiliyov.idempify.core;

/**
 * Where an operation stands in its lifecycle.
 * <p>
 * Only {@link #IN_PROCESS} and {@link #PROCESSED} are ever written to the store; {@link #CONFLICT} exists
 * only in memory.
 */
public enum OperationStatus {

    /**
     * A request claimed this key and its business operation has not finished yet. This is the state a row is
     * created in.
     */
    IN_PROCESS,

    /**
     * Another request is already processing this key, so the current one cannot proceed on its own.
     * <p>
     * Never persisted, and currently never assigned either: it belongs to the lock-based branch, where a
     * duplicate can observe an unfinished operation. The transactional branch cannot reach it - there a
     * duplicate blocks until the first request commits and then finds {@link #PROCESSED}.
     */
    CONFLICT,

    /**
     * The business operation finished and its result is stored, so further requests with this key replay that
     * result instead of running anything - until the row's TTL expires.
     */
    PROCESSED
}
