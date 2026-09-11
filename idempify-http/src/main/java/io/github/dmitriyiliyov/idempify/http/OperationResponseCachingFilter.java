package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationState;
import io.github.dmitriyiliyov.idempify.core.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class OperationResponseCachingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationResponseCachingFilter.class);
    private final IdempotentRequestMatcher matcher;
    private final OperationStateChannel channel;
    private final FingerprintMatcher fingerprintMatcher;
    private final KeyExtractor keyExtractor;
    private final ResponseManager responseManager;
    private final ObjectMapper problemDetailMapper;
    private final Clock clock;

    /**
     * The mapper is not used as given: the filter serializes problems with a copy of it that carries
     * {@link ProblemDetailJacksonMixin}. Without that mixin the problem's own properties end up nested under
     * {@code properties}, while the advice - which writes through Spring's message converter - always answers
     * flat. The copy makes both shapes equal whatever mapper the application configured, and leaves the
     * caller's own mapper untouched.
     */
    public OperationResponseCachingFilter(IdempotentRequestMatcher matcher,
                                          OperationStateChannel channel,
                                          FingerprintMatcher fingerprintMatcher,
                                          KeyExtractor keyExtractor,
                                          ResponseManager responseManager,
                                          ObjectMapper mapper,
                                          Clock clock) {
        this.matcher = Objects.requireNonNull(matcher, "matcher cannot be null");
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        this.responseManager = Objects.requireNonNull(responseManager, "responseManager cannot be null");
        this.problemDetailMapper = Objects.requireNonNull(mapper, "mapper cannot be null")
                .copy()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        OperationMetadata metadata = matcher.match(request);
        if (metadata == null) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest wrappedRequest = wrapRequest(metadata, request);

        UUID idempotencyKey = extractIdempotencyKey(metadata, wrappedRequest);
        if (idempotencyKey == null) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // matching fingerprint
        String fingerprint = generateFingerprint(idempotencyKey, metadata, wrappedRequest, response);
        if (metadata.useFingerprint() && fingerprint != null && fingerprint.isBlank()) {
            return;
        }

        // cache check
        boolean shouldReturn = cacheCheck(idempotencyKey, metadata, fingerprint, request, response);
        if (shouldReturn) {
            return;
        }

        // cache put
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            cachePut(idempotencyKey, metadata, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private HttpServletRequest wrapRequest(OperationMetadata metadata, HttpServletRequest request) {
        return metadata.useFingerprint()
                ? new ContentCachingRequestWrapper(request)
                : request;
    }

    private UUID extractIdempotencyKey(OperationMetadata metadata, HttpServletRequest request) {
        if (!metadata.useHeaderName()) {
            log.info("Idempotency key of {} comes from an expression, so the response cache cannot be reached here", request.getRequestURI());
            return null;
        }

        String headerName = metadata.getHeaderName();

        try {
            return keyExtractor.extract(headerName, new HttpRequestContext(request));
        } catch (IdempotencyKeyException e) {
            log.debug("Cannot extract idempotency key from header {}, leaving it to the aspect: {}", headerName, e.getMessage());
            return null;
        }
    }

    private String generateFingerprint(UUID idempotencyKey, OperationMetadata metadata,
                                       HttpServletRequest request, HttpServletResponse response) {
        if (metadata.useFingerprint()) {
            String fingerprint;
            try {
                FingerprintPolicy policy = metadata.getFingerprintPolicy();
                fingerprint = policy.generate(new HttpRequestContext(request));
            } catch (Exception e) {
                log.error("Error when generating fingerprint", e);
                FingerprintExceptionFilterUtils.ofExceptionallyGenerate(
                        request,
                        response,
                        idempotencyKey,
                        problemDetailMapper,
                        clock.instant()
                );
                return "";
            }

            if (fingerprint == null || fingerprint.isBlank()) {
                FingerprintExceptionFilterUtils.ofInvalid(
                        request,
                        response,
                        idempotencyKey,
                        problemDetailMapper,
                        clock.instant()
                );
                return "";
            }

            return fingerprint;
        }
        return null;
    }

    private boolean cacheCheck(UUID idempotencyKey,
                               OperationMetadata metadata,
                               String fingerprint,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        Optional<ResponseContainer> nullableResponseContainer = findResponse(idempotencyKey);
        if (nullableResponseContainer.isEmpty()) {
            return false;
        }
        ResponseContainer responseContainer = nullableResponseContainer.get();

        if (metadata.useFingerprint()) {
            try {
                fingerprintMatcher.match(
                        fingerprint,
                        responseContainer.getFingerprint(),
                        metadata.getFingerprintPolicy(),
                        idempotencyKey
                );
            } catch (Exception e) {
                log.error("Operation (idempotencyKey={}) fingerprint matching failed when checking cache", idempotencyKey);
                FingerprintExceptionFilterUtils.ofMismatch(
                        request,
                        response,
                        idempotencyKey,
                        problemDetailMapper,
                        clock.instant()
                );
                return true;
            }
        }
        FilterUtils.writeToServletResponse(idempotencyKey, response, responseContainer.getResponse());
        return true;
    }

    private Optional<ResponseContainer> findResponse(UUID idempotencyKey) {
        try {
            return responseManager.findByIdempotencyKey(idempotencyKey);
        } catch (Exception e) {
            log.error("Error when checking cache for operation response (idempotencyKey={})", idempotencyKey, e);
            return Optional.empty();
        }
    }

    private void cachePut(UUID idempotencyKey,
                          OperationMetadata metadata,
                          ContentCachingResponseWrapper responseWrapper) {
        try {
            try {
                OperationState operationState = channel.consume();
                if (operationState == null || operationState.replayed()) {
                    return;
                }
            } catch (Exception e) {
                log.error("Channel interrupted when consuming operation state (idempotencyKey={})", idempotencyKey, e);
                return;
            }

            ResponseConfig responseConfig = metadata.getResponseConfig();
            if (!FilterUtils.shouldCache(responseConfig, responseWrapper.getStatus())) {
                return;
            }

            Response operationResponse = new HttpResponseProvider(responseConfig).provide(responseWrapper);

            // DISCUSS is concurrently involve possible
            responseManager.save(idempotencyKey, operationResponse);
        } catch (Exception e) {
            log.error("Error when saving operation response (idempotencyKey={})", idempotencyKey, e);
        }
    }
}
