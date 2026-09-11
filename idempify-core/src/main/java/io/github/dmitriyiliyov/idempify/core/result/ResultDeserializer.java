package io.github.dmitriyiliyov.idempify.core.result;

/**
 * Reads a stored result back into the type the intercepted method declares, so a duplicate call can be
 * answered with the value the first one produced.
 * <p>
 * The type arrives as a {@link ResultType}, which carries a {@link java.lang.reflect.Type} and therefore
 * keeps type arguments: read {@link ResultType#getType()}.
 * <p>
 * The result comes back as {@code Object} - the library wraps arbitrary methods and learns their return type
 * only at runtime.
 */
public interface ResultDeserializer {

    /**
     * Deserializes a stored result.
     *
     * @param rawResult what {@link ResultSerializer} wrote for this operation; never {@code null} - an empty
     *                  column is read back as no result, without asking a deserializer.
     * @param type      the declared return type of the intercepted method.
     * @return the value the serializer was given; never {@code null}.
     */
    Object deserialize(String rawResult, ResultType type);
}
