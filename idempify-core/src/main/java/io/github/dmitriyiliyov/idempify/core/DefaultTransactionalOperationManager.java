package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class DefaultTransactionalOperationManager implements TransactionalOperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTransactionalOperationManager.class);
    private final OperationMapper mapper;
    private final TransactionalOperationRepository repository;
    private final FingerprintMatcher fingerprintMatcher;
    private final ResultSerializer resultSerializer;
    private final ResultDeserializer resultDeserializer;
    private final Clock clock;

    public DefaultTransactionalOperationManager(OperationMapper mapper,
                                                TransactionalOperationRepository repository,
                                                FingerprintMatcher fingerprintMatcher,
                                                ResultSerializer resultSerializer,
                                                ResultDeserializer resultDeserializer,
                                                Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public OperationDetail startOrReply(OperationContext context, OperationMetadata metadata) {
        Operation operation = saveOrFetch(context, metadata);

        if (metadata.useFingerprint() && !operation.isFirstAttempt()) {
            if (context.getFingerprint().isEmpty()) {
                log.warn("Operation (idempotencyKey={}) fingerprint is null or blank", operation.getIdempotencyKey());
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

        boolean replayed = false;
        Object result = null;

        if (OperationStatus.PROCESSED.equals(operation.getStatus())) {
            replayed = true;
            result = resultDeserializer.deserialize(operation.getResult(), context.getOperationResultType());
        }

        return new DefaultOperationDetail(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                replayed,
                result,
                operation.getExpiresAt()
        );
    }

    protected Operation saveOrFetch(OperationContext context, OperationMetadata metadata) {
        String fingerprint = context.getFingerprint().isEmpty() ? null : context.getFingerprint().get();
        Operation toSave = mapper.toOperation(
                context.getIdempotencyKey(),
                fingerprint,
                metadata,
                clock.instant()
        );

        Operation exists = repository.saveIfAbsent(toSave);

        if (exists.isExpired(clock.instant())) {
            exists = repository.update(toSave, OperationStatus.PROCESSED);
        }

        return exists;
    }

//    protected <T> OperationPropagation<T> handleConflict(OperationContext<T> context, OperationMetadata metadata) {
//        log.debug("Concurrent operation (idempotencyKey={}) execution conflict", context.getIdempotencyKey());
//
//        eventListener.onConflict();
//
//        ConflictHandler conflictHandler = metadata.getConflictHandler();
//
//        ConflictContext<T> conflictContext = new DefaultConflictContext<>(
//                context.getIdempotencyKey(),
//                context.getOperationResultType()
//        );
//
//        return DefaultOperationPropagation.ofCallback(
//                new DefaultOperationPropagation.DefaultCallback<>(
//                        () -> conflictHandler.handle(conflictContext),
//                        conflictHandler.requiresTransaction()
//                )
//        );
//    }

    @Override
    public OperationDetail complete(UUID idempotencyKey, Duration ttl, Object result) {
        Instant expiresAt = clock.instant().plus(ttl);
        Operation operation = repository.saveResultAndUpdateStatus(
                resultSerializer.serialize(result),
                OperationStatus.PROCESSED,
                expiresAt,
                idempotencyKey,
                OperationStatus.IN_PROCESS
        );
        return new DefaultOperationDetail(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                false,
                result,
                operation.getExpiresAt()
        );
    }
}
