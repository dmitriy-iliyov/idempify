package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class DefaultResponse implements Response {

    private final int status;
    private final byte [] body;
    private final String contentType;
    private final Map<String, String> headers;

    public DefaultResponse(int status, byte [] body, String contentType, Map<String, String> headers) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
        this.headers = headers == null ? Map.of() : headers;
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
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DefaultResponse that = (DefaultResponse) o;
        return status == that.status &&
                Objects.deepEquals(body, that.body) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, Arrays.hashCode(body), contentType, headers);
    }

    @Override
    public String toString() {
        return "DefaultResponse{" +
                "status=" + status +
                ", bodyLength=" + (body == null ? 0 : body.length) +
                ", contentType='" + contentType + '\'' +
                ", headers=" + headers +
                '}';
    }
}
