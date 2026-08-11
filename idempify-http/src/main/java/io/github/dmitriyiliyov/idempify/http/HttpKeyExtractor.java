package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.UuidUtils;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;

import java.util.UUID;

public class HttpKeyExtractor implements KeyExtractor {

    @Override
    public UUID extract(String headerName, RequestContext context) {
        String rawIdempotencyKey = context.getHeader(headerName);

        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw new IdempotencyKeyException("HTTP header %s is null or empty".formatted(headerName));
        }

        UUID idempotencyKey = UuidUtils.parseCanonical(rawIdempotencyKey);
        if (idempotencyKey == null) {
            throw new IdempotencyKeyException(
                    "HTTP header %s value '%s' is not a valid UUID".formatted(headerName, rawIdempotencyKey)
            );
        }

        return idempotencyKey;
    }

    @Override
    public RequestType getRequestType() {
        return RequestType.HTTP;
    }
}
