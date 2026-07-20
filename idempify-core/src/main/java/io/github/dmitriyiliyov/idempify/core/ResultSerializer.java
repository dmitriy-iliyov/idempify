package io.github.dmitriyiliyov.idempify.core;

/**
 * Defines the contract for serializing the result of an idempotent operation.
 */
public interface ResultSerializer {
    /**
     * Serializes the given result into a string.
     *
     * @param result the result to serialize.
     * @param <T>    the type of the result.
     * @return the serialized result.
     */
    <T> String serialize(T result);
}
