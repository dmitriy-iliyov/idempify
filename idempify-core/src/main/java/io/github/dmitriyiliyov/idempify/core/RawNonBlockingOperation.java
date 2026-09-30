package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

public record RawNonBlockingOperation(
        UUID idempotencyKey,
        OperationStatus status,
        Boolean isFirstAttempt,
        String result,
        String exceptionClassName,
        String response,
        String fingerprint,
        Long remainingMillis,
        Instant createdAt,
        UUID lockToken,
        Long remainingLockMillis
) { }
