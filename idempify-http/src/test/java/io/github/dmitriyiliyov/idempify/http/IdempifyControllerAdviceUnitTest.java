package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.conflict.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempifyControllerAdviceUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final String URI_PATH = "/payments";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final IdempifyControllerAdvice tested = new IdempifyControllerAdvice(clock);
    private final HttpServletRequest request = new MockHttpServletRequest("POST", URI_PATH);

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempifyControllerAdvice(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT class should be annotated as a rest controller advice")
    void class_shouldBeAnnotatedAsRestControllerAdvice() {
        assertThat(IdempifyControllerAdvice.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
    }

    @Test
    @DisplayName("UT every handler should take the request so that the problem can name the failing endpoint")
    void everyHandler_shouldTakeRequestSoThatProblemCanNameFailingEndpoint() {
        List<Method> handlers = Arrays.stream(IdempifyControllerAdvice.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class))
                .toList();

        assertThat(handlers).isNotEmpty();
        assertThat(handlers).allSatisfy(handler ->
                assertThat(handler.getParameterTypes()).contains(HttpServletRequest.class));
    }

    @Test
    @DisplayName("UT handleIdempotencyKeyException() should answer 400 with the invalid key type")
    void handleIdempotencyKeyException_shouldAnswer400WithInvalidKeyType() {
        // when
        ProblemDetail detail = tested.handleIdempotencyKeyException(
                new IdempotencyKeyException("HTTP header Idempotency-Key is null or empty"), request);

        // then
        assertProblem(detail, HttpStatus.BAD_REQUEST, ProblemTypes.INVALID_IDEMPOTENCY_KEY);
        assertThat(detail.getDetail()).isEqualTo("HTTP header Idempotency-Key is null or empty");
    }

    @Test
    @DisplayName("UT handleEmptyRequestBodyException() should answer 400 with the empty body type")
    void handleEmptyRequestBodyException_shouldAnswer400WithEmptyBodyType() {
        // when
        ProblemDetail detail = tested.handleEmptyRequestBodyException(
                new EmptyRequestBodyException("body is empty"), request);

        // then
        assertProblem(detail, HttpStatus.BAD_REQUEST, ProblemTypes.EMPTY_REQUEST_BODY);
        assertThat(detail.getDetail()).isEqualTo("body is empty");
    }

    @Test
    @DisplayName("UT handleIdempotencyConflictException() should answer 409 with the in process type")
    void handleIdempotencyConflictException_shouldAnswer409WithInProcessType() {
        // when
        ProblemDetail detail = tested.handleIdempotencyConflictException(
                new IdempotencyConflictException("already in process"), request);

        // then
        assertProblem(detail, HttpStatus.CONFLICT, ProblemTypes.OPERATION_IN_PROCESS);
        assertThat(detail.getDetail()).isEqualTo("already in process");
    }

    @Test
    @DisplayName("UT handleFingerprintMismatchException() should answer 422 with the key reuse type and the key")
    void handleFingerprintMismatchException_shouldAnswer422WithKeyReuseTypeAndKey() {
        // when
        ProblemDetail detail = tested.handleFingerprintMismatchException(
                new FingerprintMismatchException(mismatchContext(KEY)), request);

        // then
        assertProblem(detail, HttpStatus.UNPROCESSABLE_ENTITY, ProblemTypes.IDEMPOTENCY_KEY_REUSE);
        assertThat(detail.getProperties()).containsEntry("idempotencyKey", KEY);
    }

    @Test
    @DisplayName("UT handleFingerprintMismatchException() when the exception carries no context should answer 422 without the key")
    void handleFingerprintMismatchException_whenExceptionCarriesNoContext_shouldAnswer422WithoutKey() {
        // when
        ProblemDetail detail = tested.handleFingerprintMismatchException(
                new FingerprintMismatchException(null, "mismatch"), request);

        // then
        assertProblem(detail, HttpStatus.UNPROCESSABLE_ENTITY, ProblemTypes.IDEMPOTENCY_KEY_REUSE);
        assertThat(detail.getProperties()).doesNotContainKey("idempotencyKey");
    }

    @Test
    @DisplayName("UT handleFingerprintMismatchException() should not leak the fingerprints of either request")
    void handleFingerprintMismatchException_shouldNotLeakFingerprintsOfEitherRequest() {
        // when
        ProblemDetail detail = tested.handleFingerprintMismatchException(
                new FingerprintMismatchException(mismatchContext(KEY)), request);

        // then
        assertThat(detail.getDetail()).doesNotContain("previous-fingerprint", "current-fingerprint");
        assertThat(detail.getProperties().values()).doesNotContain("previous-fingerprint", "current-fingerprint");
    }

    @Test
    @DisplayName("UT handleWaitAbortedException() should answer 503 with the not completed type")
    void handleWaitAbortedException_shouldAnswer503WithNotCompletedType() {
        // when
        ProblemDetail detail = tested.handleWaitAbortedException(new WaitAbortedException("gave up"), request);

        // then
        assertProblem(detail, HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.OPERATION_NOT_COMPLETED);
    }

    @Test
    @DisplayName("UT handleWaitAbortedException() when the wait timed out should answer 503 as well")
    void handleWaitAbortedException_whenWaitTimedOut_shouldAnswer503AsWell() {
        // when
        ProblemDetail detail = tested.handleWaitAbortedException(new WaitTimeoutException(KEY), request);

        // then
        assertProblem(detail, HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.OPERATION_NOT_COMPLETED);
    }

    @Test
    @DisplayName("UT handleWaitAbortedException() when the attempts ran out should answer 503 as well")
    void handleWaitAbortedException_whenAttemptsRanOut_shouldAnswer503AsWell() {
        // when
        ProblemDetail detail = tested.handleWaitAbortedException(new WaitAttemptsExhaustedException(KEY), request);

        // then
        assertProblem(detail, HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.OPERATION_NOT_COMPLETED);
    }

    @Test
    @DisplayName("UT handleInvalidFingerprintException() should answer the same way the filter does, carrying the key")
    void handleInvalidFingerprintException_shouldAnswerSameWayFilterDoesCarryingKey() {
        // when
        ProblemDetail detail = tested.handleInvalidFingerprintException(new InvalidFingerprintException(KEY), request);

        // then
        assertProblem(detail, HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.FINGERPRINT_POLICY_BROKEN);
        assertThat(detail.getProperties()).containsEntry("idempotencyKey", KEY);
    }

    @Test
    @DisplayName("UT handleOperationStatusMismatchException() should answer 500 without exposing the internal message")
    void handleOperationStatusMismatchException_shouldAnswer500WithoutExposingInternalMessage() {
        // given
        OperationStatusMismatchException exception =
                new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS);

        // when
        ProblemDetail detail = tested.handleOperationStatusMismatchException(exception, request);

        // then
        assertProblem(detail, HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.IDEMPOTENT_PROCESSING_FAILED);
        assertThat(detail.getDetail()).isNotEqualTo(exception.getMessage());
    }

    @Test
    @DisplayName("UT handleOperationDisappearedException() should answer 500 without exposing the internal message")
    void handleOperationDisappearedException_shouldAnswer500WithoutExposingInternalMessage() {
        // given
        OperationDisappearedException exception = new OperationDisappearedException(KEY);

        // when
        ProblemDetail detail = tested.handleOperationDisappearedException(exception, request);

        // then
        assertProblem(detail, HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.IDEMPOTENT_PROCESSING_FAILED);
        assertThat(detail.getDetail()).isNotEqualTo(exception.getMessage());
    }

    @Test
    @DisplayName("UT handleIdempotentProcessingException() should answer 500 without exposing the internal message")
    void handleIdempotentProcessingException_shouldAnswer500WithoutExposingInternalMessage() {
        // given
        IdempotentProcessingException exception =
                new IdempotentProcessingException("could not serialize the result of table operations");

        // when
        ProblemDetail detail = tested.handleIdempotentProcessingException(exception, request);

        // then
        assertProblem(detail, HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.IDEMPOTENT_PROCESSING_FAILED);
        assertThat(detail.getDetail()).isNotEqualTo(exception.getMessage());
    }

    @Test
    @DisplayName("UT handleSerializationProcessingException() should answer 500 without exposing the internal message")
    void handleSerializationProcessingException_shouldAnswer500WithoutExposingInternalMessage() {
        // given
        SerializationProcessingException exception =
                new SerializationProcessingException("Cannot construct instance of ResponseEntity", new IllegalStateException());

        // when
        ProblemDetail detail = tested.handleSerializationProcessingException(exception, request);

        // then
        assertProblem(detail, HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.SERIALIZATION_PROCESSING_FAILED);
        assertThat(detail.getDetail()).isNotEqualTo(exception.getMessage());
        assertThat(detail.getTitle()).isEqualTo("Serialization processing failed");
    }

    @Test
    @DisplayName("UT handleSerializationProcessingException() should answer the same way for a failure on either side")
    void handleSerializationProcessingException_shouldAnswerSameWayForFailureOnEitherSide() {
        // given - the handler is declared on the group, so both sides have to reach it
        SerializationProcessingException read = new DeserializationException("read failed", new IllegalStateException());
        SerializationProcessingException write = new SerializationException("write failed", new IllegalStateException());

        // when / then
        assertProblem(tested.handleSerializationProcessingException(read, request),
                HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.SERIALIZATION_PROCESSING_FAILED);
        assertProblem(tested.handleSerializationProcessingException(write, request),
                HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.SERIALIZATION_PROCESSING_FAILED);
    }

    @Test
    @DisplayName("UT handlers when the endpoint is under a context path should name the full request pattern")
    void handlers_whenEndpointIsUnderContextPath_shouldNameFullRequestUri() {
        // given
        MockHttpServletRequest prefixed = new MockHttpServletRequest("POST", "/api/payments");
        prefixed.setContextPath("/api");

        // when
        ProblemDetail detail = tested.handleIdempotencyConflictException(
                new IdempotencyConflictException("already in process"), prefixed);

        // then
        assertThat(detail.getInstance()).isEqualTo(URI.create("/api/payments"));
    }

    private void assertProblem(ProblemDetail detail, HttpStatus status, URI type) {
        assertThat(detail.getStatus()).isEqualTo(status.value());
        assertThat(detail.getType()).isEqualTo(type);
        assertThat(detail.getTitle()).isNotBlank();
        assertThat(detail.getInstance()).isEqualTo(URI.create(URI_PATH));
        assertThat(detail.getProperties()).containsEntry("timestamp", NOW);
    }

    private static FingerprintMismatchContext mismatchContext(UUID idempotencyKey) {
        return new FingerprintMismatchContext() {

            @Override
            public UUID getIdempotencyKey() {
                return idempotencyKey;
            }

            @Override
            public String getPreviousFingerprint() {
                return "previous-fingerprint";
            }

            @Override
            public String getCurrentFingerprint() {
                return "current-fingerprint";
            }
        };
    }
}
