package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

public class ThrowingEmptyBodyFallback implements EmptyBodyFallback {

    @Override
    public byte [] fallback(RequestContext context) {
        throw new EmptyRequestBodyException(context);
    }
}
