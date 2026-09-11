package io.github.dmitriyiliyov.idempify.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * The single table of "what went wrong" -> status, {@link ProblemTypes type} and title.
 */
final class ProblemDetailFactory {

    private static final String IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String TIMESTAMP = "timestamp";

    private ProblemDetailFactory() {}

    static ProblemDetail invalidIdempotencyKey(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.INVALID_IDEMPOTENCY_KEY,
                "Invalid Idempotency-Key",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail emptyRequestBody(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.EMPTY_REQUEST_BODY,
                "Empty request body",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail operationInProcess(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.OPERATION_IN_PROCESS,
                "Operation is already in process",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail operationNotCompleted(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemTypes.OPERATION_NOT_COMPLETED,
                "Operation is still in process",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail idempotencyKeyReuse(String instance, Instant timestamp, UUID idempotencyKey) {
        ProblemDetail detail = problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemTypes.IDEMPOTENCY_KEY_REUSE,
                "Idempotency-Key reuse",
                "This Idempotency-Key was already used with a different request body.",
                instance,
                timestamp
        );
        return withKey(detail, idempotencyKey);
    }

    static ProblemDetail fingerprintPolicyBroken(String detail, String instance, Instant timestamp, UUID idempotencyKey) {
        ProblemDetail problemDetail = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.FINGERPRINT_POLICY_BROKEN,
                "Fingerprint Policy broken",
                detail,
                instance,
                timestamp
        );
        return withKey(problemDetail, idempotencyKey);
    }

    static ProblemDetail idempotentProcessingFailed(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.IDEMPOTENT_PROCESSING_FAILED,
                "Idempotent processing failed",
                detail,
                instance,
                timestamp
        );
    }

    static ProblemDetail serializationProcessingFailed(String detail, String instance, Instant timestamp) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.SERIALIZATION_PROCESSING_FAILED,
                "Serialization processing failed",
                detail,
                instance,
                timestamp
        );
    }

    private static ProblemDetail problem(HttpStatus status,
                                         URI type,
                                         String title,
                                         String detail,
                                         String instance,
                                         Instant timestamp) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(instance));
        problemDetail.setProperty(TIMESTAMP, timestamp);
        return problemDetail;
    }

    private static ProblemDetail withKey(ProblemDetail detail, UUID idempotencyKey) {
        if (idempotencyKey != null) {
            detail.setProperty(IDEMPOTENCY_KEY, idempotencyKey);
        }
        return detail;
    }
}
