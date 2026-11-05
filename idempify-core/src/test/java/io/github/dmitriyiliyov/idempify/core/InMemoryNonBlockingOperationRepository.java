package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Stands in for the store of the non-blocking branch, holding to the contract of
 * {@link NonBlockingOperationRepository}: the claim is a lease counted by the store's clock and taken on a row
 * of any status once nobody holds it, every way out of the claim is conditional on the token, the expiry is
 * set from completion, and reads answer with what is left of the record and of the lease.
 * <p>
 * A conditional write that misses throws: what a miss means is not settled yet (S24), and a fake that quietly
 * ignored one would hide it. The one exception is {@link #unlock}: both processors release the claim after
 * the row may already be gone, so a miss there is part of the ordinary flow.
 * <p>
 * Writes made while a {@link RecordingTransactionManager} transaction is open are undone by its rollback, as
 * they would be on a store sharing the business transaction; the {@code byAnotherCall} helpers stand for
 * other connections and are never undone.
 */
final class InMemoryNonBlockingOperationRepository implements NonBlockingOperationRepository, TransactionBoundResource {

    private final TestClock clock;
    private final Map<UUID, Row> rows = new HashMap<>();
    private final Deque<Runnable> undoLog = new ArrayDeque<>();
    private final Deque<Runnable> beforeFinds = new ArrayDeque<>();
    private final Map<Write, RuntimeException> failures = new EnumMap<>(Write.class);
    private boolean inTransaction;

    InMemoryNonBlockingOperationRepository(TestClock clock) {
        this.clock = clock;
    }

    @Override
    public boolean lockOrSaveAndLock(RawNonBlockingOperation operation, long lockTtlMillis) {
        failIfAsked(Write.CLAIM);
        Instant now = clock.instant();
        Row stored = rows.get(operation.idempotencyKey());
        if (stored == null) {
            write(new Row(
                    operation.idempotencyKey(),
                    OperationStatus.IN_PROCESS,
                    null,
                    null,
                    operation.fingerprint(),
                    now,
                    operation.lockToken(),
                    now.plusMillis(lockTtlMillis),
                    null
            ));
            return true;
        }
        if (stored.isLockedAt(now) && !operation.lockToken().equals(stored.lockToken())) {
            return false;
        }
        write(stored.withLock(operation.lockToken(), now.plusMillis(lockTtlMillis)));
        return true;
    }

    @Override
    public Optional<RawNonBlockingOperation> find(UUID idempotencyKey) {
        if (!beforeFinds.isEmpty()) {
            beforeFinds.poll().run();
        }
        return Optional.ofNullable(rows.get(idempotencyKey)).map(this::read);
    }

    @Override
    public RawNonBlockingOperation updateByLockToken(RawNonBlockingOperation operation, Set<OperationStatus> onStatuses) {
        Row stored = heldRow(operation.idempotencyKey(), operation.lockToken());
        if (!onStatuses.contains(stored.status())) {
            throw new OperationLockLostException(operation.idempotencyKey());
        }
        Row reclaimed = new Row(
                stored.idempotencyKey(),
                operation.status(),
                null,
                null,
                operation.fingerprint(),
                stored.createdAt(),
                stored.lockToken(),
                stored.lockUntil(),
                null
        );
        write(reclaimed);
        return read(reclaimed);
    }

    @Override
    public void saveResultAndUpdateStatusByLockToken(UUID idempotencyKey,
                                                     String result,
                                                     OperationStatus newStatus,
                                                     long ttlMillis,
                                                     UUID lockToken,
                                                     OperationStatus onStatus) {
        Row stored = heldRow(idempotencyKey, lockToken);
        if (stored.status() != onStatus) {
            throw new OperationLockLostException(idempotencyKey);
        }
        write(stored.withOutcome(newStatus, result, null, clock.instant().plusMillis(ttlMillis)));
    }

    @Override
    public void saveExceptionAndUpdateStatusByLockToken(UUID idempotencyKey,
                                                        String exception,
                                                        ResultType type,
                                                        OperationStatus newStatus,
                                                        long ttlMillis,
                                                        UUID lockToken,
                                                        OperationStatus onStatus) {
        failIfAsked(Write.KEEP_FAILURE);
        Row stored = heldRow(idempotencyKey, lockToken);
        if (stored.status() != onStatus) {
            throw new OperationLockLostException(idempotencyKey);
        }
        String exceptionClassName = ((Class<?>) type.getType()).getName();
        write(stored.withOutcome(newStatus, exception, exceptionClassName, clock.instant().plusMillis(ttlMillis)));
    }

    @Override
    public void deleteByLockToken(UUID idempotencyKey, UUID lockToken) {
        failIfAsked(Write.DELETE);
        heldRow(idempotencyKey, lockToken);
        Row previous = rows.remove(idempotencyKey);
        if (inTransaction) {
            undoLog.push(() -> rows.put(idempotencyKey, previous));
        }
    }

    @Override
    public void unlock(UUID idempotencyKey, UUID lockToken) {
        failIfAsked(Write.UNLOCK);
        Row stored = rows.get(idempotencyKey);
        if (stored != null && lockToken.equals(stored.lockToken())) {
            write(stored.withLock(null, null));
        }
    }

    /**
     * Another call has claimed the key and is still running its operation.
     */
    void claimedByAnotherCall(UUID idempotencyKey, UUID lockToken, Duration lease) {
        claimedByAnotherCall(idempotencyKey, null, lockToken, lease);
    }

    /**
     * Another call, whose request had this fingerprint, has claimed the key; a row already there keeps its own.
     */
    void claimedByAnotherCall(UUID idempotencyKey, String fingerprint, UUID lockToken, Duration lease) {
        Instant now = clock.instant();
        Row stored = rows.get(idempotencyKey);
        rows.put(idempotencyKey, stored == null
                ? new Row(idempotencyKey, OperationStatus.IN_PROCESS, null, null, fingerprint, now, lockToken, now.plus(lease), null)
                : stored.withLock(lockToken, now.plus(lease)));
    }

    /**
     * The call holding the key completes its operation and lets the claim go.
     */
    void completedByAnotherCall(UUID idempotencyKey, String result, Duration ttl) {
        Row stored = rows.get(idempotencyKey);
        rows.put(idempotencyKey, stored
                .withOutcome(OperationStatus.PROCESSED, result, null, clock.instant().plus(ttl))
                .withLock(null, null));
    }

    /**
     * The call holding the key keeps its failure on the record and lets the claim go.
     */
    void failedByAnotherCall(UUID idempotencyKey, Throwable failure, Duration ttl) {
        Row stored = rows.get(idempotencyKey);
        rows.put(idempotencyKey, stored
                .withOutcome(OperationStatus.EXCEPTIONALLY_PROCESSED, failure.getMessage(), failure.getClass().getName(),
                        clock.instant().plus(ttl))
                .withLock(null, null));
    }

    /**
     * The row goes away behind the processor's back - an external cleanup, a manual delete.
     */
    void deletedByAnotherCall(UUID idempotencyKey) {
        rows.remove(idempotencyKey);
    }

    /**
     * Runs the given actions one per read, in order, before the read answers.
     */
    void beforeFinds(Runnable... actions) {
        beforeFinds.addAll(List.of(actions));
    }

    void failNext(Write write, RuntimeException failure) {
        failures.put(write, failure);
    }

    Optional<Row> row(UUID idempotencyKey) {
        return Optional.ofNullable(rows.get(idempotencyKey));
    }

    @Override
    public void begin() {
        inTransaction = true;
        undoLog.clear();
    }

    @Override
    public void commit() {
        inTransaction = false;
        undoLog.clear();
    }

    @Override
    public void rollback() {
        inTransaction = false;
        while (!undoLog.isEmpty()) {
            undoLog.pop().run();
        }
    }

    private void failIfAsked(Write write) {
        RuntimeException failure = failures.remove(write);
        if (failure != null) {
            throw failure;
        }
    }

    private Row heldRow(UUID idempotencyKey, UUID lockToken) {
        Row stored = rows.get(idempotencyKey);
        if (stored == null || !Objects.equals(lockToken, stored.lockToken())) {
            throw new OperationLockLostException(idempotencyKey);
        }
        return stored;
    }

    private void write(Row row) {
        Row previous = rows.put(row.idempotencyKey(), row);
        if (inTransaction) {
            undoLog.push(() -> {
                if (previous == null) {
                    rows.remove(row.idempotencyKey());
                } else {
                    rows.put(row.idempotencyKey(), previous);
                }
            });
        }
    }

    private RawNonBlockingOperation read(Row row) {
        long now = clock.instant().toEpochMilli();
        return new RawNonBlockingOperation(
                row.idempotencyKey(),
                row.status(),
                false,
                row.result(),
                row.exceptionClassName(),
                null,
                row.fingerprint(),
                row.expiresAt() == null ? null : row.expiresAt().toEpochMilli() - now,
                row.createdAt(),
                row.lockToken(),
                row.lockUntil() == null ? null : row.lockUntil().toEpochMilli() - now
        );
    }

    enum Write {
        CLAIM,
        KEEP_FAILURE,
        DELETE,
        UNLOCK
    }

    record Row(
            UUID idempotencyKey,
            OperationStatus status,
            String result,
            String exceptionClassName,
            String fingerprint,
            Instant createdAt,
            UUID lockToken,
            Instant lockUntil,
            Instant expiresAt
    ) {

        boolean isLockedAt(Instant now) {
            return lockToken != null && lockUntil.isAfter(now);
        }

        Row withLock(UUID lockToken, Instant lockUntil) {
            return new Row(idempotencyKey, status, result, exceptionClassName, fingerprint, createdAt, lockToken, lockUntil, expiresAt);
        }

        Row withOutcome(OperationStatus status, String result, String exceptionClassName, Instant expiresAt) {
            return new Row(idempotencyKey, status, result, exceptionClassName, fingerprint, createdAt, lockToken, lockUntil, expiresAt);
        }
    }
}
