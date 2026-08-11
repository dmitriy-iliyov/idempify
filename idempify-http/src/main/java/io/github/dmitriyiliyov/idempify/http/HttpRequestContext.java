package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HttpRequestContext implements RequestContext {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestContext.class);
    private final HttpServletRequest request;

    public HttpRequestContext(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public RequestType getRequestType() {
        return RequestType.HTTP;
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getPath() {
        return request.getRequestURI();
    }

    @Override
    public byte [] getBodyBytes() {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Error when reading request body", e);
            throw new RuntimeException("Error when reading request body", e);
        }
    }

    @Override
    public String toString() {
        return "HttpRequestContext{" +
                "request=" + request +
                '}';
    }
}
