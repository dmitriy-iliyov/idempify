package io.github.dmitriyiliyov.idempify.core;

/**
 * Reads a stored result back into the type the intercepted method declares, so a duplicate call can be
 * answered with the value the first one produced.
 * <p>
 * The type arrives as a {@link ResultType}, which carries a {@link java.lang.reflect.Type} and therefore
 * keeps type arguments: read {@link ResultType#getType()}, which is what a serialization library needs and
 * what a {@code Class} could not express.
 * <p>
 * The result comes back as {@code Object}. The library wraps arbitrary methods and never knows their
 * concrete return type, so a type parameter here would promise a guarantee nobody can keep.
 */
public interface ResultDeserializer {

    /**
     * Deserializes a stored result.
     *
     * @param rawResult what {@link ResultSerializer} wrote for this operation.
     * @param type      the declared return type of the intercepted method.
     */
    Object deserialize(String rawResult, ResultType type);
}
