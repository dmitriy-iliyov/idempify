package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class DefaultNonBlockingOperationManager implements NonBlockingOperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultNonBlockingOperationManager.class);
    private final NonBlockingOperationRepository repository;
    private final LockTokenGenerator lockTokenGenerator;
    private final OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> serializer;
    private final OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserializer;
    private final FingerprintMatcher fingerprintMatcher;
    private final ResultSerializer resultSerializer;
    private final IdempotencyEventListener eventListener;

    public DefaultNonBlockingOperationManager(NonBlockingOperationRepository repository,
                                              LockTokenGenerator lockTokenGenerator,
                                              OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> serializer,
                                              OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserializer,
                                              FingerprintMatcher fingerprintMatcher,
                                              ResultSerializer resultSerializer,
                                              IdempotencyEventListener eventListener) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.lockTokenGenerator = Objects.requireNonNull(lockTokenGenerator, "lockTokenGenerator cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
    }

    @Override
    public LockDetail tryLock(OperationContext context, OperationMetadata metadata) {
        NonBlockingOperation operation = NonBlockingOperation.ofContext(
                context,
                lockTokenGenerator.generate()
        );

        boolean isAcquired = repository.lockOrSaveAndLock(
                serializer.serialize(operation),
                metadata.getLockTtl().toMillis()
        );

        return new DefaultLockDetail(isAcquired, operation.getLockToken());
    }

    @Override
    public NonBlockingOperationDetail fetchAndValidate(OperationContext context,
                                                       OperationMetadata metadata,
                                                       LockDetail lockDetail) {
        NonBlockingOperation operation = fetch(context, lockDetail);
        matchFingerprint(context, metadata, operation);
        operation = handleConflict(context, metadata, operation);
        return new DefaultNonBlockingOperationDetail(
                lockDetail,
                operation,
                !OperationStatus.isProcessedStatus(operation.getStatus())
        );
    }

    private NonBlockingOperation fetch(OperationContext context, LockDetail lockDetail) {
        NonBlockingOperation operation;
        if (lockDetail.isAcquired()) {
            RawNonBlockingOperation rawOperation = repository.find(context.getIdempotencyKey())
                    .orElseThrow(() -> new OperationDisappearedException(
                            "Operation (idempotencyKey=%s) was claimed, but its record is gone".formatted(context.getIdempotencyKey())
                    ));
            operation = deserializer.deserialize(rawOperation, context.getResultType());

            if (operation.isExpired()) {
                NonBlockingOperation newOperation = NonBlockingOperation.ofContext(
                        context,
                        lockDetail.getLockToken()
                );
                operation = deserializer.deserialize(
                        repository.updateByLockToken(
                                serializer.serialize(newOperation),
                                OperationStatus.PROCESSED_STATUSES
                        ),
                        operation.getResultType()
                );
            }

        } else {
            operation = NonBlockingOperation.ofConflict(context);
        }

        return operation;
    }

    private void matchFingerprint(OperationContext context,
                                  OperationMetadata metadata,
                                  NonBlockingOperation operation) {
        if (metadata.useFingerprint()) {
            if (context.getFingerprint().isEmpty()) {
                log.error("Operation (idempotencyKey={}) fingerprint is null or blank", operation.getIdempotencyKey());
                throw new InvalidFingerprintException(operation.getIdempotencyKey());
            } else {
                fingerprintMatcher.match(
                        context.getFingerprint().get(),
                        operation.getFingerprint(),
                        metadata.getFingerprintPolicy(),
                        operation.getIdempotencyKey()
                );
            }
        }
    }

    private NonBlockingOperation handleConflict(OperationContext context,
                                                OperationMetadata metadata,
                                                NonBlockingOperation operation) {
        if (operation.hasConflict()) {
            log.debug("Detected concurrent operation (idempotencyKey={}) execution", context.getIdempotencyKey());
            eventListener.onConflict();
            ConflictHandler conflictHandler = metadata.getConflictHandler();
            operation = conflictHandler
                    .handle(new DefaultConflictContext(context.getIdempotencyKey(), context.getResultType()));
            if (operation == null) {
                throw new ConflictHandlerResultException("""
                        ConflictHandler %s returned null (idempotencyKey=%s), \
                        but must return a completed operation or throw"""
                        .formatted(conflictHandler.getClass().getName(), context.getIdempotencyKey())
                );
            } else if (!OperationStatus.isProcessedStatus(operation.getStatus())) {
                throw new ConflictHandlerResultException("""
                        ConflictHandler %s returned operation (idempotencyKey=%s) with %s status, \
                        but must return a completed operation (with one of %s status) or throw"""
                        .formatted(conflictHandler.getClass().getName(),
                                context.getIdempotencyKey(),
                                operation.getStatus(),
                                OperationStatus.PROCESSED_STATUSES)
                );
            }
        }

        return operation;
    }

    @Override
    public NonBlockingOperationDetail complete(NonBlockingOperationDetail detail,
                                               Object result,
                                               Duration ttl) {
        OperationStatus newStatus = OperationStatus.PROCESSED;
        repository.saveResultAndUpdateStatusByLockToken(
                detail.getIdempotencyKey(),
                result == null ? null : resultSerializer.serialize(result),
                newStatus,
                ttl.toMillis(),
                detail.getLockToken(),
                OperationStatus.IN_PROCESS
        );
        return new DefaultNonBlockingOperationDetail(
                detail.isAcquired(),
                detail.getLockToken(),
                detail.getIdempotencyKey(),
                newStatus,
                result,
                true
        );
    }

    @Override
    public void rollback(UUID idempotencyKey, UUID lockToken) {
        repository.deleteByLockToken(idempotencyKey, lockToken);
    }

    @Override
    public void completeExceptionally(OperationContext context, Throwable t, Duration ttl, UUID lockToken) {
        repository.saveExceptionAndUpdateStatusByLockToken(
                context.getIdempotencyKey(),
                resultSerializer.serialize(t),
                ResultType.ofClass(t.getClass()),
                OperationStatus.EXCEPTIONALLY_PROCESSED,
                ttl.toMillis(),
                lockToken
        );
    }

    @Override
    public void unlock(UUID idempotencyKey, UUID lockToken) {
        repository.unlock(idempotencyKey, lockToken);
    }



    public static class DefaultLockDetail implements LockDetail {

        private final UUID lockToken;
        private final boolean isAcquired;

        public DefaultLockDetail(boolean isAcquired,
                                 UUID lockToken) {
            this.lockToken = lockToken;
            this.isAcquired = isAcquired;
        }

        @Override
        public UUID getLockToken() {
            return lockToken;
        }

        @Override
        public boolean isAcquired() {
            return isAcquired;
        }
    }
}
