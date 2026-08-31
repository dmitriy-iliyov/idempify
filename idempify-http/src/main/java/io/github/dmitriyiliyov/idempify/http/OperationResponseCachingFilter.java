package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.response.*;
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
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class OperationResponseCachingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationResponseCachingFilter.class);
    private final IdempotentRequestMatcher matcher;
    private final OperationStateChannel channel;
    private final FingerprintMatcher fingerprintMatcher;
    private final KeyExtractor keyExtractor;
    private final ResponseCache cache;
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
                                          ResponseCache cache,
                                          ObjectMapper mapper,
                                          Clock clock) {
        this.matcher = Objects.requireNonNull(matcher, "matcher cannot be null");
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        this.fingerprintMatcher = Objects.requireNonNull(fingerprintMatcher, "fingerprintMatcher cannot be null");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
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

        HttpServletRequest wrappedRequest = metadata.useFingerprint()
                ? new ContentCachingRequestWrapper(request)
                : request;

        UUID idempotencyKey = extractIdempotencyKey(metadata, wrappedRequest);
        if (idempotencyKey == null) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

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
            cachePut(idempotencyKey, fingerprint, metadata, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
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
        CachedResponse cachedResponse = findInCache(idempotencyKey);
        if (cachedResponse != null) {
            if (metadata.useFingerprint()) {
                try {
                    fingerprintMatcher.match(
                            fingerprint,
                            cachedResponse.getFingerprint(),
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
            writeCachedResponse(response, cachedResponse, idempotencyKey);
            return true;
        }
        return false;
    }

    private CachedResponse findInCache(UUID idempotencyKey) {
        try {
            return cache.findByIdempotencyKey(idempotencyKey);
        } catch (Exception e) {
            log.error("Error when checking cache for operation response (idempotencyKey={})", idempotencyKey, e);
            return null;
        }
    }

    private void writeCachedResponse(HttpServletResponse response,
                                     CachedResponse cachedResponse,
                                     UUID idempotencyKey) throws IOException {
        response.setStatus(cachedResponse.getStatus());

        String contentType = cachedResponse.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            response.setContentType(contentType);
        } else {
            log.warn("Operation (idempotencyKey={}) response contentType is null or blank", idempotencyKey);
        }

        byte [] body = cachedResponse.getBody();
        int length = body == null ? 0 : body.length;
        response.setContentLength(length);

        if (length > 0) {
            response.getOutputStream().write(body);
        }
    }

    private void cachePut(UUID idempotencyKey,
                          String fingerprint,
                          OperationMetadata metadata,
                          ContentCachingResponseWrapper responseWrapper) {
        try {
            OperationState operationState = channel.consume();
            if (operationState == null || operationState.replayed()) {
                return;
            }

            ResponseCacheConfig cacheConfig = metadata.getResponseCacheConfig();
            if (!shouldCache(cacheConfig, responseWrapper.getStatus())) {
                return;
            }

            CachedResponse cacheableResponse = new DefaultCachedResponse(
                    responseWrapper.getStatus(),
                    responseWrapper.getContentAsByteArray(),
                    responseWrapper.getContentType(),
                    fingerprint
            );

            Duration ttl = cacheTtl(operationState);
            if (ttl == null) {
                log.debug("Operation (idempotencyKey={}) has no expiry yet, response not cached", idempotencyKey);
                return;
            }
            cache.save(idempotencyKey, cacheableResponse, ttl);
        } catch (Exception e) {
            log.error("Error when saving operation response (idempotencyKey={})", idempotencyKey, e);
        }
    }

    private boolean shouldCache(ResponseCacheConfig cacheConfig, int status) {
        if (cacheConfig == null) {
            return false;
        }

        boolean shouldCache = cacheConfig.isEnabled();

        if (is4xx(status) && !cacheConfig.shouldCache4xx()) {
            shouldCache = false;
        }

        if (is5xx(status) && !cacheConfig.shouldCache5xx()) {
            shouldCache = false;
        }

        return shouldCache;
    }

    private boolean is4xx(int status) {
        return status >= 400 && status < 500;
    }

    private boolean is5xx(int status) {
        return status >= 500 && status < 600;
    }

    private Duration cacheTtl(OperationState operationState) {
        if (operationState.getExpiresAt() == null) {
            return null;
        }
        Duration ttl = Duration.between(clock.instant(), operationState.getExpiresAt());
        return ttl.isPositive() ? ttl : null;
    }
}
