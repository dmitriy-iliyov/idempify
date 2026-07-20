package io.github.dmitriyiliyov.idempify.core;

/**
 * Defines the contract for deserializing the result of an idempotent operation.
 */
public interface ResultDeserializer {
    /**
     * Deserializes the given raw result into an object of the specified type.
     *
     * @param rawResult the raw result to deserialize.
     * @param c         the type of the response.
     * @param <T>       the type of the response.
     * @return the deserialized result.
     */
    <T> T deserialize(String rawResult, Class<T> c);
}
