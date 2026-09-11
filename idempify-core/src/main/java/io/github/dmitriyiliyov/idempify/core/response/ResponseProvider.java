package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Collects a {@link Response} out of what a transport has already written, so that the part which knows how
 * a response is shaped stays in the transport module.
 *
 * @param <T> whatever the transport hands over to be read - for Spring MVC the wrapper the filter buffered
 *            the response in.
 */
public interface ResponseProvider<T> {
    Response provide(T src);
}
