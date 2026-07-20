package io.github.dmitriyiliyov.idempify.core;

/**
 * Provides access to the context of the current request.
 */
public interface RequestContext {

    /**
     * Returns the type of the request.
     */
    RequestType getRequestType();

    /**
     * Returns the value of the specified header.
     *
     * @param name the name of the header.
     */
    String getHeader(String name);

    /**
     * Returns the HTTP method of the request.
     */
    String getMethod();

    /**
     * Returns the path of the request.
     */
    String getPath();

    /**
     * Returns the body of the request.
     */
    String getBody();
}
