package io.github.dmitriyiliyov.idempify.core.result;


/**
 * Renders the result of a completed operation into the string the store keeps, so a later call under the same
 * key can be answered without running anything.
 * <p>
 * Must round-trip with the {@link ResultDeserializer} configured beside it: what one writes, the other reads
 * back into the method's declared return type.
 * <p>
 * The value arrives as {@code Object}: a serializer walks the object it is given, and only the way back needs
 * a declared type.
 */
public interface ResultSerializer {

    /**
     * Serializes a result for storage.
     *
     * @param result the value the business operation returned; never {@code null} - an operation with no
     *               result to keep is written as an empty column, without asking a serializer.
     * @return the text to store; never {@code null}.
     */
    String serialize(Object result);
}
