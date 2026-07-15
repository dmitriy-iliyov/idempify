package io.github.dmitriyiliyov.springidempotency.core;

import java.util.UUID;

public interface KeyExtractor {
    UUID extract(String headerName, RequestContext context);
    RequestType getRequestType();
}
