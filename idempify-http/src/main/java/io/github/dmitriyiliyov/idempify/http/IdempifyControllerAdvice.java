package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.IdempotentProcessingException;
import io.github.dmitriyiliyov.idempify.core.conflict.IdempotencyConflictException;
import io.github.dmitriyiliyov.idempify.core.conflict.OperationDisappearedException;
import io.github.dmitriyiliyov.idempify.core.conflict.WaitAbortedException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
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
    public ProblemDetail handleIdempotencyKeyException(IdempotencyKeyException e, HttpServletRequest request) {
        return ProblemFactory.invalidIdempotencyKey(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(EmptyRequestBodyException.class)
    public ProblemDetail handleEmptyRequestBodyException(EmptyRequestBodyException e, HttpServletRequest request) {
        return ProblemFactory.emptyRequestBody(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflictException(IdempotencyConflictException e, HttpServletRequest request) {
        return ProblemFactory.operationInProcess(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(FingerprintMismatchException.class)
    public ProblemDetail handleFingerprintMismatchException(FingerprintMismatchException e, HttpServletRequest request) {
        FingerprintMismatchContext context = e.getContext();
        UUID idempotencyKey = context == null ? null : context.getIdempotencyKey();
        return ProblemFactory.idempotencyKeyReuse(request.getRequestURI(), clock.instant(), idempotencyKey);
    }

    @ExceptionHandler(WaitAbortedException.class)
    public ProblemDetail handleWaitAbortedException(WaitAbortedException e, HttpServletRequest request) {
        return ProblemFactory.operationNotCompleted(e.getMessage(), request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(OperationDisappearedException.class)
    public ProblemDetail handleOperationDisappearedException(OperationDisappearedException e, HttpServletRequest request) {
        log.error("Waited for an operation that disappeared from the storage", e);
        return ProblemFactory.idempotentProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }

    @ExceptionHandler(IdempotentProcessingException.class)
    public ProblemDetail handleIdempotentProcessingException(IdempotentProcessingException e, HttpServletRequest request) {
        log.error("Idempotent processing failed", e);
        return ProblemFactory.idempotentProcessingFailed(FAILED_DETAIL, request.getRequestURI(), clock.instant());
    }
}
