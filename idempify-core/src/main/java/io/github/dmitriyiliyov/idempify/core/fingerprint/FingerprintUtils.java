package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

public final class FingerprintUtils {

    private FingerprintUtils() {}

    /**
     * Opens a fingerprint with the request's path and method, each length-prefixed so that a value
     * containing the separator cannot be read back as two fields.
     */
    public static StringBuilder toStringBuilderWithoutBody(RequestContext context) {
        StringBuilder sb = new StringBuilder();

        String path = context.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Request path is null or blank; cannot generate fingerprint");
        }
        sb.append(path.length())
                .append("|")
                .append(path);

        String method = context.getMethod();
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Request method is null or blank; cannot generate fingerprint");
        }
        sb.append("|")
                .append(method.length())
                .append("|")
                .append(method);

        return sb;
    }
}
