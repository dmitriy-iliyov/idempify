package io.github.dmitriyiliyov.springidempotency.core;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompositeKeyExtractor implements KeyExtractor {

    private final Map<RequestType, KeyExtractor> extractors;

    public CompositeKeyExtractor(List<KeyExtractor> extractors) {
        Objects.requireNonNull(extractors, "extractors cannot be null");
        this.extractors = new EnumMap<>(
                extractors.stream()
                        .collect(Collectors.toMap(KeyExtractor::getRequestType, Function.identity()))
        );
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
        throw new IllegalStateException("Should not be called");
    }
}
