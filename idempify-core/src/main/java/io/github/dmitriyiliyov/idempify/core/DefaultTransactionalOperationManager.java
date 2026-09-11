package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the conversion between the domain and the store: the repository below deals in rows, and this is the
 * one place that knows what those rows decode into. The declared type of a result comes from the call site
 * rather than the row, so a caller without one - the response path - never has to invent it.
 */
public class DefaultTransactionalOperationManager implements TransactionalOperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTransactionalOperationManager.class);
    private final OperationCreator creator;
    private final TransactionalOperationRepository repository;
    private final FingerprintMatcher fingerprintMatcher;
    private final OperationSerializer serializer;
    private final OperationDeserializer deserializer;
    private final ResultSerializer resultSerializer;
    private final Clock clock;

    public DefaultTransactionalOperationManager(OperationCreator creator,
                                                TransactionalOperationRepository repository,
                                                FingerprintMatcher fingerprintMatcher,
                                                OperationSerializer serializer,
                                                OperationDeserializer deserializer,
                                                ResultSerializer resultSerializer,
                                                Clock clock) {
        this.creator = Objects.requireNonNull(creator, "creator cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer cannot be null");
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public OperationDetail startOrReply(OperationContext context, OperationMetadata metadata) {
        Operation operation = saveOrFetch(context);

        if (metadata.useFingerprint() && !operation.isFirstAttempt()) {
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

        boolean replayed = false;
        Object result = null;

        if (OperationStatus.PROCESSED.equals(operation.getStatus())) {
            replayed = true;
            result = operation.getResult();
        }

        return new DefaultOperationDetail(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                replayed,
                result
        );
    }

    protected Operation saveOrFetch(OperationContext context) {
        RawOperation rawToSave = serializer.serialize(
                creator.create(context, clock.instant())
        );

        Operation exists = deserializer.deserialize(
                repository.saveIfAbsent(rawToSave),
                context.getResultType()
        );

        if (exists.isExpired(clock.instant())) {
            exists = deserializer.deserialize(
                    repository.update(rawToSave, OperationStatus.PROCESSED),
                    context.getResultType()
            );
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
    public OperationDetail complete(UUID idempotencyKey, Object result, ResultType resultType, Duration ttl) {
        Instant expiresAt = clock.instant().plus(ttl);
        Operation operation = deserializer.deserialize(
                repository.saveResultAndUpdateStatus(
                        idempotencyKey,
                        resultSerializer.serialize(result),
                        OperationStatus.PROCESSED,
                        expiresAt,
                        OperationStatus.IN_PROCESS
                ),
                resultType
        );
        return new DefaultOperationDetail(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                false,
                operation.getResult()
        );
    }
}
