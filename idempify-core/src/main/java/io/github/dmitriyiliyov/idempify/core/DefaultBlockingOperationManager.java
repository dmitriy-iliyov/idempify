package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the conversion between the domain and the store: the repository below deals in rows, and this is the
 * one place that knows what those rows decode into. The declared type of a result comes from the call site
 * rather than the row.
 */
public class DefaultBlockingOperationManager implements BlockingOperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultBlockingOperationManager.class);
    private final BlockingOperationRepository repository;
    private final FingerprintMatcher fingerprintMatcher;
    private final OperationSerializer<Operation, RawOperation> serializer;
    private final OperationDeserializer<RawOperation, Operation> deserializer;
    private final ResultSerializer resultSerializer;

    public DefaultBlockingOperationManager(BlockingOperationRepository repository,
                                           FingerprintMatcher fingerprintMatcher,
                                           OperationSerializer<Operation, RawOperation> serializer,
                                           OperationDeserializer<RawOperation, Operation> deserializer,
                                           ResultSerializer resultSerializer) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer cannot be null");
        this.resultSerializer = Objects.requireNonNull(resultSerializer, "resultSerializer cannot be null");
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

        return new DefaultOperationDetail(
                operation.getIdempotencyKey(),
                operation.getStatus(),
                operation.getResult(),
                !OperationStatus.PROCESSED.equals(operation.getStatus())
        );
    }

    protected Operation saveOrFetch(OperationContext context) {
        RawOperation serialized = serializer.serialize(
                Operation.ofContext(context)
        );

        Operation exists = deserializer.deserialize(
                repository.saveIfAbsent(serialized),
                context.getResultType()
        );

        if (exists.isExpired()) {
            exists = deserializer.deserialize(
                    repository.update(serialized, OperationStatus.PROCESSED),
                    context.getResultType()
            );
        }

        return exists;
    }

    @Override
    public OperationDetail complete(UUID idempotencyKey, Object result, Duration ttl) {
        OperationStatus newStatus = OperationStatus.PROCESSED;
        repository.saveResultAndUpdateStatus(
                idempotencyKey,
                result == null ? null : resultSerializer.serialize(result),
                newStatus,
                ttl.toMillis(),
                OperationStatus.IN_PROCESS
        );
        return new DefaultOperationDetail(
                idempotencyKey,
                newStatus,
                result,
                true
        );
    }
}
