package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes the problem responses of the filter, which runs outside the {@code DispatcherServlet} and so is never
 * reached by {@link IdempifyControllerAdvice}. What a failure looks like to a client is decided in
 * {@link ProblemDetailFactory}, shared with the advice; here lives only how it reaches the wire.
 */
public final class FingerprintExceptionFilterUtils {

    private static final Logger log = LoggerFactory.getLogger(FingerprintExceptionFilterUtils.class);

    private FingerprintExceptionFilterUtils() {}

    public static void ofInvalid(HttpServletRequest request,
                                 HttpServletResponse response,
                                 UUID idempotencyKey,
                                 ObjectMapper problemDetailMapper,
                                 Instant timestamp) {
        ProblemDetail detail = ProblemDetailFactory.fingerprintPolicyBroken(
                "Fingerprint policy is broken, returned fingerprint is null or blank.",
                request.getRequestURI(),
                timestamp,
                idempotencyKey
        );
        write(response, detail, problemDetailMapper, "a null or blank fingerprint", idempotencyKey);
    }

    public static void ofMismatch(HttpServletRequest request,
                                  HttpServletResponse response,
                                  UUID idempotencyKey,
                                  ObjectMapper problemDetailMapper,
                                  Instant timestamp) {
        ProblemDetail detail = ProblemDetailFactory.idempotencyKeyReuse(
                request.getRequestURI(),
                timestamp,
                idempotencyKey
        );
        write(response, detail, problemDetailMapper, "a fingerprint mismatch", idempotencyKey);
    }

    public static void ofExceptionallyGenerate(HttpServletRequest request,
                                               HttpServletResponse response,
                                               UUID idempotencyKey,
                                               ObjectMapper problemDetailMapper,
                                               Instant timestamp) {
        ProblemDetail detail = ProblemDetailFactory.fingerprintPolicyBroken(
                "Generate fingerprint throw",
                request.getRequestURI(),
                timestamp,
                idempotencyKey
        );
        write(response, detail, problemDetailMapper, "a exceptionally generating fingerprint", idempotencyKey);
    }

    private static void write(HttpServletResponse response,
                              ProblemDetail detail,
                              ObjectMapper problemDetailMapper,
                              String cause,
                              UUID idempotencyKey) {
        try {
            response.setStatus(detail.getStatus());
            response.setContentType("application/problem+json");
            response.getWriter().write(problemDetailMapper.writeValueAsString(detail));
        } catch (IOException e) {
            log.error("Error when writing response for {} (idempotencyKey={})", cause, idempotencyKey, e);
        }
    }
}
