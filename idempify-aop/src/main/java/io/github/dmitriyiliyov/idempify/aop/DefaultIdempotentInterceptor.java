package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;

import java.util.Objects;
import java.util.UUID;

public class DefaultIdempotentInterceptor implements IdempotentInterceptor {

    private final KeyExtractor keyExtractor;
    private final FingerprintManager fingerprintManager;
    private final IdempotentProcessor processor;

    public DefaultIdempotentInterceptor(KeyExtractor keyExtractor, FingerprintManager fingerprintManager, IdempotentProcessor processor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        this.fingerprintManager = Objects.requireNonNull(fingerprintManager, "fingerprintManager cannot be null");
        this.processor = Objects.requireNonNull(processor, "processor cannot be null");
    }

    @Override
    public <T> T intercept(InterceptContext<T> context) {

        UUID idempotencyKey = context.getIdempotencyKey() == null
                ? keyExtractor.extract(context.getHeaderName(), context.getRequestContext())
                : context.getIdempotencyKey();

        OperationMetadata.Builder metadataBuilder = OperationMetadata.builder()
                .idempotencyKey(idempotencyKey)
                .ttl(context.getTtl())
                .timeUnit(context.getTimeUnit())
                .conflictHandleStrategy(context.getConflictHandleStrategy());

        if (ConflictHandleStrategy.CUSTOM.equals(context.getConflictHandleStrategy())) {
                metadataBuilder.conflictHandlerClass(context.getConflictHandlerClass());
        }

        if (context.useFingerprint()) {
            metadataBuilder
                    .useFingerprint(true)
                    .fingerprint(fingerprintManager.generate(
                            context.getRequestContext(),
                            context.getFingerprintPolicyClass())
                    )
                    .fingerprintPolicyClass(context.getFingerprintPolicyClass());
        }

        return processor.process(metadataBuilder.build(), context.getOperationResultType(), context.getOperation());
    }
}
