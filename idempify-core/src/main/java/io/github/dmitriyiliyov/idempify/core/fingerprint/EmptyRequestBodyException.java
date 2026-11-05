package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

public class EmptyRequestBodyException extends FingerprintException {

    public EmptyRequestBodyException(String message) {
        super(message);
    }

    public EmptyRequestBodyException(RequestContext context) {
        super("Request (path=%s, method=%s) body is null or empty"
                .formatted(context.getPath(), context.getMethod()));
    }
}
