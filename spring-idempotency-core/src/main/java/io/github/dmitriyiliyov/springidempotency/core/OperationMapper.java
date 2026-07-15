package io.github.dmitriyiliyov.springidempotency.core;

import java.time.Instant;

public interface OperationMapper {
    Operation toOperation(OperationMetadata metadata, Instant instant);
}
