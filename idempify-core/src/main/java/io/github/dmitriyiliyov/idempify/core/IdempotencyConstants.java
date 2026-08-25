package io.github.dmitriyiliyov.idempify.core;

import java.time.Duration;

public final class IdempotencyConstants {

    public static final String HEADER_NAME = "Idempotency-Key";
    public static final Duration TTL = Duration.ofHours(24);
    public static final ProcessorType PROCESSOR_TYPE = ProcessorType.LOCK_BASED;

    private IdempotencyConstants() {}
}
