package io.github.dmitriyiliyov.idempify.core;

import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * A transaction manager over in-memory resources: a rollback - asked for, marked with
 * {@code setRollbackOnly()}, or forced by a failing commit - undoes what the enlisted resources were given
 * since the transaction began. Like a real one it binds the transaction to the thread, so code running inside
 * sees {@link TransactionSynchronizationManager#isActualTransactionActive()}.
 */
final class RecordingTransactionManager implements PlatformTransactionManager {

    private final List<TransactionBoundResource> resources;
    private int commits;
    private int rollbacks;
    private boolean failsNextCommit;
    private boolean failsNextBegin;

    RecordingTransactionManager(TransactionBoundResource... resources) {
        this.resources = List.of(resources);
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        if (failsNextBegin) {
            failsNextBegin = false;
            throw new CannotCreateTransactionException("begin failed");
        }
        resources.forEach(TransactionBoundResource::begin);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
        if (status.isRollbackOnly()) {
            rollback(status);
            return;
        }
        if (failsNextCommit) {
            failsNextCommit = false;
            rollback(status);
            throw new TransactionSystemException("commit failed");
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        resources.forEach(TransactionBoundResource::commit);
        commits++;
    }

    @Override
    public void rollback(TransactionStatus status) {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        resources.forEach(TransactionBoundResource::rollback);
        rollbacks++;
    }

    void failNextBegin() {
        this.failsNextBegin = true;
    }

    void failNextCommit() {
        this.failsNextCommit = true;
    }

    int commits() {
        return commits;
    }

    int rollbacks() {
        return rollbacks;
    }
}
