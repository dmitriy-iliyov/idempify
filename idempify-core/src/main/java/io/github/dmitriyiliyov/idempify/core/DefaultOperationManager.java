package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.DelegatingConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class DefaultOperationManager implements OperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperationManager.class);
    private final OperationMapper mapper;
    private final OperationRepository repository;
    private final IdempotencyEventListener eventListener;
    private final DelegatingConflictHandler conflictHandler;
    private final FingerprintManager fingerprintManager;
    private final ResultSerializer resultSerializer;
    private final ResultDeserializer resultDeserializer;
    private final Clock clock;

    public DefaultOperationManager(OperationMapper mapper,
                                   OperationRepository repository,
                                   IdempotencyEventListener eventListener,
                                   DelegatingConflictHandler conflictHandler, FingerprintManager fingerprintManager,
                                   ResultSerializer resultSerializer,
                                   ResultDeserializer resultDeserializer,
                                   Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
        this.conflictHandler = Objects.requireNonNull(conflictHandler, "conflictHandlerClass cannot be null");
        this.fingerprintManager = Objects.requireNonNull(fingerprintManager, "fingerprintManager cannot be null");
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public <T> Optional<T> startOrReply(OperationMetadata metadata, Class<T> c) {
        Operation operation = saveOrFetch(metadata);

        Optional<T> result;
        if (OperationState.CONFLICT.equals(operation.getState())) {
            result = handleConflict(metadata, c);
        } else if (OperationState.PROCESSED.equals(operation.getState())) {
            if (metadata.useFingerprint()) {
                result = compareFingerprint(operation, metadata, c);
            } else {
                result = Optional.of(resultDeserializer.deserialize(operation.getResult(), c));
            }
        } else {
            result = Optional.empty();
        }

        return result;
    }

    private Operation saveOrFetch(OperationMetadata metadata) {
        Operation toInsert = mapper.toOperation(metadata, clock.instant());

        Operation exists = repository.saveIfAbsent(toInsert);

        if (exists.hasConflict()) {
            exists.setState(OperationState.CONFLICT);
        }

        if (exists.isExpired(clock.instant())) {
            exists = repository.update(toInsert, OperationState.PROCESSED);
        }

        return exists;
    }

    private <T> Optional<T> handleConflict(OperationMetadata metadata, Class<T> c) {
        log.debug("Concurrent operation (idempotencyKey={}) execution conflict", metadata.getIdempotencyKey());
        eventListener.onConflict();
        return conflictHandler.handle(metadata, c);
    }

    private <T> Optional<T> compareFingerprint(Operation operation, OperationMetadata metadata, Class<T> c) {
        log.debug("Comparing operation (idempotencyKey={}) fingerprint", operation.getIdempotencyKey());

        boolean isFingerprintSame = fingerprintManager.compareWith(
                operation.getFingerprint(),
                metadata.getFingerprint(),
                metadata.getFingerprintPolicyClass()
        );

        if (isFingerprintSame) {
            return Optional.of(resultDeserializer.deserialize(operation.getResult(), c));
        } else {
            log.debug("Fingerprint mismatch for operation (idempotencyKey={}), start handling", operation.getIdempotencyKey());
            eventListener.onFingerprintMismatch();
            fingerprintManager.handleMismatch(
                    new DefaultFingerprintMismatchContext(
                            operation.getIdempotencyKey(),
                            operation.getFingerprint(),
                            metadata.getFingerprint()
                    ),
                    metadata.getFingerprintPolicyClass()
            );
            return Optional.empty();
        }
    }

    @Override
    public <T> T complete(OperationMetadata metadata, T response) {
        repository.saveResultAndUpdateState(
                resultSerializer.serialize(response),
                OperationState.PROCESSED,
                metadata.getIdempotencyKey(),
                OperationState.IN_PROCESS
        );
        return response;
    }
}
