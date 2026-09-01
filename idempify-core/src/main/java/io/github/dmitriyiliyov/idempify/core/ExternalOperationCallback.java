package io.github.dmitriyiliyov.idempify.core;

/**
 * The caller's business operation, wrapped so that the library can decide whether to run it at all.
 * <p>
 * Declared to throw {@link Throwable} because it stands in for an arbitrary intercepted method, including one
 * that declares checked exceptions.
 */
@FunctionalInterface
public interface ExternalOperationCallback {

    /**
     * Runs the business operation and returns its result.
     */
    Object call() throws Throwable;
}
