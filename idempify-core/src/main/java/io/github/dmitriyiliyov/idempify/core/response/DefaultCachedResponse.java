package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Arrays;

public final class DefaultCachedResponse implements CachedResponse {

    private final int status;
    private final byte [] body;
    private final String contentType;
    private final String fingerprint;

    public DefaultCachedResponse(int status, byte [] body, String contentType, String fingerprint) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
        this.fingerprint = fingerprint;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public byte [] getBody() {
        return body;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCachedResponse that)) {
            return false;
        }
        return status == that.status && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(status) + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "DefaultCachedResponse{" +
                "status=" + status +
                ", bodyLength=" + (body == null ? 0 : body.length) +
                '}';
    }
}
