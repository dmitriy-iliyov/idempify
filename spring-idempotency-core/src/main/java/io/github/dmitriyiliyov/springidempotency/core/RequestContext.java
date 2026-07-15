package io.github.dmitriyiliyov.springidempotency.core;

public interface RequestContext {

    RequestType getRequestType();

    String getHeader(String name);

    String getMethod();

    String getPath();

    String getBody();

    Object getNativeRequest();
}
