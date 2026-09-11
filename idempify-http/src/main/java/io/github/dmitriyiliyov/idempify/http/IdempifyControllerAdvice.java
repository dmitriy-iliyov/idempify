package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.IdempotentProcessingException;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.SerializationProcessingException;
import io.github.dmitriyiliyov.idempify.core.conflict.IdempotencyConflictException;
import io.github.dmitriyiliyov.idempify.core.conflict.OperationDisappearedException;
import io.github.dmitriyiliyov.idempify.core.conflict.WaitAbortedException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@RestControllerAdvice
public class IdempifyControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(IdempifyControllerAdvice.class);
    private static final String FAILED_DETAIL = "The operation could not be completed idempotently.";

    private final Clock clock;

    public IdempifyControllerAdvice(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @ExceptionHandler(IdempotencyKeyException.class)
    public ProblemDetail handleIdempotencyKeyException(IdempotencyKeyException e,
                                                       HttpServletRequest request) {
        return ProblemDetailFactory.invalidIdempotencyKey(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(EmptyRequestBodyException.class)
    public ProblemDetail handleEmptyRequestBodyException(EmptyRequestBodyException e,
                                                         HttpServletRequest request) {
        return ProblemDetailFactory.emptyRequestBody(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflictException(IdempotencyConflictException e,
                                                            HttpServletRequest request) {
        return ProblemDetailFactory.operationInProcess(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(FingerprintMismatchException.class)
    public ProblemDetail handleFingerprintMismatchException(FingerprintMismatchException e,
                                                            HttpServletRequest request) {
        FingerprintMismatchContext context = e.getContext();
        UUID idempotencyKey = context == null ? null : context.getIdempotencyKey();
        return ProblemDetailFactory.idempotencyKeyReuse(request.getRequestURI(), clock.instant(), idempotencyKey);
    }

    @ExceptionHandler(InvalidFingerprintException.class)
    public ProblemDetail handleInvalidFingerprintException(InvalidFingerprintException e,
                                                           HttpServletRequest request) {
        return ProblemDetailFactory.fingerprintPolicyBroken(
                e.getMessage(),
                request.getRequestURI(),
                clock.instant(),
                e.getIdempotencyKey()
        );
    }

    @ExceptionHandler(WaitAbortedException.class)
    public ProblemDetail handleWaitAbortedException(WaitAbortedException e,
                                                    HttpServletRequest request) {
        return ProblemDetailFactory.operationNotCompleted(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(OperationDisappearedException.class)
    public ProblemDetail handleOperationDisappearedException(OperationDisappearedException e,
                                                             HttpServletRequest request) {
        log.error("Waited for an operation that disappeared from the storage", e);
        return ProblemDetailFactory.idempotentProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(OperationStatusMismatchException.class)
    public ProblemDetail handleOperationStatusMismatchException(OperationStatusMismatchException e,
                                                               HttpServletRequest request) {
        log.error("Operation was not in the status its caller was holding it in", e);
        return ProblemDetailFactory.idempotentProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(IdempotentProcessingException.class)
    public ProblemDetail handleIdempotentProcessingException(IdempotentProcessingException e,
                                                             HttpServletRequest request) {
        log.error("Idempotent processing failed", e);
        return ProblemDetailFactory.idempotentProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(SerializationProcessingException.class)
    public ProblemDetail handleSerializationProcessingException(SerializationProcessingException e,
                                                               HttpServletRequest request) {
        log.error("Serialization processing failed", e);
        return ProblemDetailFactory.serializationProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }
}
