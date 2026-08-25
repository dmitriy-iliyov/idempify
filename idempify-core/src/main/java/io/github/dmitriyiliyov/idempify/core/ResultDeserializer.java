package io.github.dmitriyiliyov.idempify.core;

/**
 * Reads a stored result back into the type the intercepted method declares, so a duplicate call can be
 * answered with the value the first one produced.
 * <p>
 * The type arrives as a plain {@link Class} taken from the method signature, so generic return types do not
 * survive erasure - a limitation the caller inherits, not one an implementation can work around.
 */
public interface ResultDeserializer {

    /**
     * Deserializes a stored result.
     *
     * @param rawResult what {@link ResultSerializer} wrote for this operation.
     * @param c         the declared return type of the intercepted method.
     * @param <T>       the type of the result.
     */
    <T> T deserialize(String rawResult, Class<T> c);
}
