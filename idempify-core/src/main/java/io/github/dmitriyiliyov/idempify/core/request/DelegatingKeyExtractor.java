package io.github.dmitriyiliyov.idempify.core.request;

import java.util.*;

public class DelegatingKeyExtractor implements KeyExtractor {

    private final Map<RequestType, KeyExtractor> extractors;

    public DelegatingKeyExtractor(List<KeyExtractor> extractors) {
        Objects.requireNonNull(extractors, "extractors cannot be null");
        if (extractors.isEmpty()) {
            throw new IllegalStateException(
                    "At least one KeyExtractor must be registered, but none was found"
            );
        }

        this.extractors = new EnumMap<>(RequestType.class);
        for (KeyExtractor extractor : extractors) {
            KeyExtractor registered = this.extractors.putIfAbsent(extractor.getRequestType(), extractor);
            if (registered != null) {
                throw new IllegalStateException(
                        "Exactly one KeyExtractor must be registered for request type %s, but both %s and %s are"
                                .formatted(extractor.getRequestType(), registered.getClass().getName(), extractor.getClass().getName())
                );
            }
        }
    }

    @Override
    public UUID extract(String headerName, RequestContext context) {
        RequestType requestType = Objects.requireNonNull(context.getRequestType(), "requestType cannot be null");
        KeyExtractor extractor = extractors.get(requestType);
        if (extractor == null) {
            throw new IllegalStateException("KeyExtractor for %s not found".formatted(requestType));
        }
        return extractor.extract(headerName, context);
    }

    @Override
    public RequestType getRequestType() {
        throw new IllegalStateException("DelegatingKeyExtractor has no single RequestType and must not be queried directly");
    }
}
