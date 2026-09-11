package io.github.dmitriyiliyov.idempify.core;

/**
 * Reduces an {@link Operation} to the row a store can hold: the result and the response become text, and
 * every other component travels as it is.
 * <p>
 * Must round-trip with the {@link OperationDeserializer} configured beside it - what one writes, the other
 * reads back.
 */
public interface OperationSerializer {
    RawOperation serialize(Operation operation);
}
