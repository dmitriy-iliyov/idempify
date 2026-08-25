package io.github.dmitriyiliyov.idempify.core;

/**
 * Renders the result of a completed operation into the string the store keeps, so a later call under the same
 * key can be answered without running anything.
 * <p>
 * Must round-trip with the {@link ResultDeserializer} configured beside it: what one writes, the other reads
 * back into the method's declared return type.
 */
public interface ResultSerializer {

    /**
     * Serializes a result for storage.
     *
     * @param result the value the business operation returned; may be {@code null} if the method legitimately
     *               returns none.
     * @param <T>    the type of the result.
     */
    <T> String serialize(T result);
}
