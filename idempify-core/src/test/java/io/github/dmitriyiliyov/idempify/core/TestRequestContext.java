package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Stands in for the transport module: core owns the {@link RequestContext} contract but no implementation of it.
 */
public final class TestRequestContext implements RequestContext {

    private final RequestType requestType;
    private final String path;
    private final String method;
    private final byte[] bodyBytes;
    private final Map<String, String> headers;

    private TestRequestContext(Builder builder) {
        this.requestType = builder.requestType;
        this.path = builder.path;
        this.method = builder.method;
        this.bodyBytes = builder.bodyBytes;
        this.headers = builder.headers;
    }

    @Override
    public RequestType getRequestType() {
        return requestType;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] getBodyBytes() {
        return bodyBytes;
    }

    public static TestRequestContext of(String path, String method, String body) {
        return builder().path(path).method(method).body(body).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private RequestType requestType = RequestType.HTTP;
        private String path = "/payments";
        private String method = "POST";
        private byte[] bodyBytes = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);
        private Map<String, String> headers = Map.of();

        private Builder() {}

        public Builder requestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder body(String body) {
            this.bodyBytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder bodyBytes(byte[] bodyBytes) {
            this.bodyBytes = bodyBytes;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public TestRequestContext build() {
            return new TestRequestContext(this);
        }
    }
}
