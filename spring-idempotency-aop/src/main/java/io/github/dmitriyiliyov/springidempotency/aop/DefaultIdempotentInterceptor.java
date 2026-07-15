package io.github.dmitriyiliyov.springidempotency.aop;

import io.github.dmitriyiliyov.springidempotency.core.IdempotentProcessor;
import io.github.dmitriyiliyov.springidempotency.core.KeyExtractor;
import io.github.dmitriyiliyov.springidempotency.core.OperationMetadata;
import io.github.dmitriyiliyov.springidempotency.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintManager;

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
    public void intercept(InterceptContext context) {

        UUID idempotencyKey = context.getIdempotencyKey() == null ?
                keyExtractor.extract(context.getAnnotation().headerName(), context.getRequestContext()) :
                context.getIdempotencyKey();

        OperationMetadata.Builder metadataBuilder = OperationMetadata.builder()
                .idempotencyKey(idempotencyKey)
                .ttl(context.getAnnotation().ttl())
                .timeUnit(context.getAnnotation().timeUnit())
                .conflictHandleStrategy(context.getAnnotation().onConflict());

        if (ConflictHandleStrategy.CUSTOM.equals(context.getAnnotation().onConflict())) {
                metadataBuilder.conflictHandlerClass(context.getAnnotation().conflictHandler());
        }

        if (context.getAnnotation().useFingerprint()) {
            metadataBuilder
                    .useFingerprint(true)
                    .fingerprint(fingerprintManager.generate(
                            context.getRequestContext(),
                            context.getAnnotation().fingerprintPolicy())
                    )
                    .fingerprintPolicyClass(context.getAnnotation().fingerprintPolicy());
        }

        processor.process(metadataBuilder.build(), context.getDelegate());
    }
}
