package io.github.dmitriyiliyov.idempify.core;

/**
 * Something a {@link RecordingTransactionManager} enlists: what it was given between {@link #begin()} and
 * {@link #rollback()} disappears, as the writes of one connection do when its transaction rolls back.
 */
interface TransactionBoundResource {

    void begin();

    void commit();

    void rollback();
}
