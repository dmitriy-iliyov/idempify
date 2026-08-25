package io.github.dmitriyiliyov.idempify.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DelegatingIdempotentProcessor implements IdempotentProcessor {

    private final Map<ProcessorType, TypeAwareIdempotentProcessor> processors;

    public DelegatingIdempotentProcessor(List<TypeAwareIdempotentProcessor> processors) {
        Objects.requireNonNull(processors, "processors cannot be null");
        if (processors.isEmpty()) {
            throw new IllegalStateException(
                    "At least one TypeAwareIdempotentProcessor must be registered, but none was found"
            );
        }

        this.processors = new EnumMap<>(ProcessorType.class);
        for (TypeAwareIdempotentProcessor processor : processors) {
            TypeAwareIdempotentProcessor registered = this.processors.putIfAbsent(processor.getType(), processor);
            if (registered != null) {
                throw new IllegalStateException(
                        "Exactly one TypeAwareIdempotentProcessor must be registered for type %s, but both %s and %s are"
                                .formatted(processor.getType(), registered.getClass().getName(), processor.getClass().getName())
                );
            }
        }
    }

    @Override
    public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
        IdempotentProcessor processor = processors.get(metadata.getProcessorType());
        if (processor == null) {
            throw new IllegalStateException(
                    "no processor found for operation (idempotencyKey=%s)".formatted(context.getIdempotencyKey())
            );
        }
        return processor.process(context, metadata);
    }
}
