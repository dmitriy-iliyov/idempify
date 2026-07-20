package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;

/**
 * Defines the contract for mapping {@link OperationMetadata} to an {@link Operation}.
 */
public interface OperationMapper {
    /**
     * Maps the given metadata to an operation.
     *
     * @param metadata the metadata to map.
     * @param instant  the current instant.
     * @return the mapped operation.
     */
    Operation toOperation(OperationMetadata metadata, Instant instant);
}
