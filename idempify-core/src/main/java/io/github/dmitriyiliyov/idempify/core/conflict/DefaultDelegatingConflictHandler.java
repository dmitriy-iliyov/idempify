package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultDelegatingConflictHandler implements DelegatingConflictHandler {

    private final Map<ConflictHandleStrategy, List<ConflictHandler>> handlers;

    public DefaultDelegatingConflictHandler(List<ConflictHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers cannot be null");
        this.handlers = new EnumMap<>(
                handlers.stream()
                        .collect(Collectors.groupingBy(ConflictHandler::getStrategy))
        );
        validateHandlers();
    }

    private void validateHandlers() {
        for (ConflictHandleStrategy strategy : ConflictHandleStrategy.values()) {
            if (!ConflictHandleStrategy.CUSTOM.equals(strategy)) {

                List<ConflictHandler> h = this.handlers.get(strategy);

                if (h == null) {
                    throw new IllegalStateException(
                            "Exactly one ConflictHandler must be registered for strategy %s, but found 0.".formatted(strategy)
                    );
                } else if (h.size() > 1) {
                    throw new IllegalStateException(
                            "Exactly one ConflictHandler must be registered for strategy %s, but found several.".formatted(strategy)
                    );
                }
            }
        }
    }

    @Override
    public <T> Optional<T> handle(OperationMetadata metadata, Class<T> c) {

        ConflictHandleStrategy strategy = metadata.getConflictHandleStrategy();

        if (ConflictHandleStrategy.CUSTOM.equals(strategy)) {
            Class<? extends ConflictHandler> conflictHandlerClass = metadata.getConflictHandlerClass();

            if (conflictHandlerClass != null) {

                ConflictHandler handler = handlers.get(strategy)
                        .stream()
                        .filter(h -> conflictHandlerClass == h.getClass())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("ConflictHandler for CUSTOM strategy not found"));

                return handler.handle(metadata.getIdempotencyKey(), c);
            } else {
                throw new IllegalStateException("ConflictHandler class must be specified when using CUSTOM strategy.");
            }
        }

        ConflictHandler toHandle = handlers.get(strategy).getFirst();

        return toHandle.handle(metadata.getIdempotencyKey(), c);
    }
}
