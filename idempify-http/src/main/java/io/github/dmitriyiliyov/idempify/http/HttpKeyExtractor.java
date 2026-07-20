package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.*;

import java.util.UUID;

public class HttpKeyExtractor implements KeyExtractor {

    @Override
    public UUID extract(String headerName, RequestContext context) {
        String rawIdempotencyKey = context.getHeader(headerName);

        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw new EmptyIdempotencyKeyException("HTTP header %s is empty or null".formatted(headerName));
        }

        try {
            return UUID.fromString(rawIdempotencyKey);
        } catch (RuntimeException re) {
            throw new InvalidIdempotencyKeyException(re);
        }
    }

    @Override
    public RequestType getRequestType() {
        return RequestType.HTTP;
    }
}
