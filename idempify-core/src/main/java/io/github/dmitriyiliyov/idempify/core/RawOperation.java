package io.github.dmitriyiliyov.idempify.core;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the operation store as the store itself sees it - every part that needs a serializer already
 * reduced to text, and nothing that needs one still an object.
 * <p>
 * Which components may be {@code null} is not this type's decision but the row's: {@code result},
 * {@code response} and {@code expiresAt} are all empty on a claim and stay empty until the operation
 * completes.
 * <p>
 * The declared type of the result is not among the components: it travels on {@link Operation} and comes from
 * the call site on every read.
 */
public record RawOperation(
        UUID idempotencyKey,
        OperationStatus status,
        Boolean isFirstAttempt,
        String result,
        String response,
        String fingerprint,
        Instant expiresAt,
        Instant createdAt
) {}
