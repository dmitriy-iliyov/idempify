package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.DefaultOperationContext;
import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.OperationContext;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;

import java.util.Objects;
import java.util.UUID;

public class DefaultIdempotentInterceptor implements IdempotentInterceptor {

    private final KeyExtractor keyExtractor;
    private final IdempotentProcessor processor;

    public DefaultIdempotentInterceptor(KeyExtractor keyExtractor,
                                        IdempotentProcessor processor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        this.processor = Objects.requireNonNull(processor, "processor cannot be null");
    }

    @Override
    public <T> T intercept(InterceptContext<T> context) {
        UUID idempotencyKey = context.getIdempotencyKey();

        if (idempotencyKey == null) {
            if (!context.getOperationMetadata().useHeaderName()) {
                throw new IllegalStateException("""
                        Operation has no idempotency key: the call site takes it from an expression rather than 
                        from a request attribute, nothing else can supply it
                """);
            }
            idempotencyKey = keyExtractor.extract(
                    context.getOperationMetadata().getHeaderName(),
                    context.getRequestContext()
            );
        }

        String fingerprint = null;
        if (context.getOperationMetadata().useFingerprint()) {
            fingerprint = context.getOperationMetadata()
                    .getFingerprintPolicy()
                    .generate(context.getRequestContext());
            Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
            if (fingerprint.isBlank()) {
                throw new IllegalArgumentException("fingerprint cannot be empty or blank");
            }
        }

        OperationContext<T> operationContext = new DefaultOperationContext<>(
                context.getOperationResultType(),
                context.getOperationCallback(),
                idempotencyKey,
                fingerprint
        );

        return processor.process(operationContext, context.getOperationMetadata());
    }
}
