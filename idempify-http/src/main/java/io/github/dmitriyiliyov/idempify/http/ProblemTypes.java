package io.github.dmitriyiliyov.idempify.http;

import java.net.URI;

/**
 * The {@code type} URIs the library puts into its problem responses.
 *
 * <p>A type identifies a kind of failure, and it is the only field of the response a client may branch on:
 * {@code status} is too coarse to tell two failures apart, and {@code detail} is prose that may be reworded or
 * translated. One type always comes with the same title and the same status; only {@code detail} and the
 * properties vary per occurrence.
 *
 * <p>These are names, not addresses - nothing is fetched from them, which is why they are URNs rather than
 * URLs of a site that would have to exist. They are part of the public contract: renaming one breaks every
 * client that branches on it.
 */
public final class ProblemTypes {

    private static final String BASE = "https://idempify.io/errors/";

    public static final URI INVALID_IDEMPOTENCY_KEY = URI.create(BASE + "invalid-idempotency-key");

    public static final URI EMPTY_REQUEST_BODY = URI.create(BASE + "empty-request-body");

    public static final URI OPERATION_IN_PROCESS = URI.create(BASE + "operation-in-process");

    public static final URI IDEMPOTENCY_KEY_REUSE = URI.create(BASE + "idempotency-key-reuse");

    public static final URI OPERATION_NOT_COMPLETED = URI.create(BASE + "operation-not-completed");

    public static final URI FINGERPRINT_POLICY_BROKEN = URI.create(BASE + "fingerprint-policy-broken");

    public static final URI IDEMPOTENT_PROCESSING_FAILED = URI.create(BASE + "idempotent-processing-failed");

    public static final URI RESULT_PROCESSING_FAILED = URI.create(BASE + "result-processing-failed");

    private ProblemTypes() {}
}
