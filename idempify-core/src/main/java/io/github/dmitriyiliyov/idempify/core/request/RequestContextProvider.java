package io.github.dmitriyiliyov.idempify.core.request;

/**
 * Supplies the {@link RequestContext} of the call currently being handled.
 * <p>
 * Implementations are transport-specific and read from a thread-bound holder, so this is only callable while
 * a request is in flight on the calling thread - outside of one there is nothing to return, and an
 * implementation is expected to throw rather than hand back {@code null}.
 */
public interface RequestContextProvider {

    /**
     * Returns the context of the current request.
     *
     * @throws IllegalStateException if no request is bound to the calling thread.
     */
    RequestContext getContext();
}
