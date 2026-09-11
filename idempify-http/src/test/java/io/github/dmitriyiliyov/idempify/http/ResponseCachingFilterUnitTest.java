package io.github.dmitriyiliyov.idempify.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.dmitriyiliyov.idempify.core.IdempotencyKeyException;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationState;
import io.github.dmitriyiliyov.idempify.core.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResponseCachingFilterUnitTest {

    private static final String HEADER_NAME = IdempifyDefaults.HEADER_NAME;
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String METHOD = "POST";
    private static final String URI = "/payments";
    private static final String BODY = "{\"amount\":10}";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plus(TTL);
    private static final String FINGERPRINT = "current-fingerprint";
    private static final String PREVIOUS_FINGERPRINT = "previous-fingerprint";

    @Mock
    IdempotentRequestMatcher matcher;

    @Mock
    FingerprintMatcher fingerprintMatcher;

    @Mock
    KeyExtractor keyExtractor;

    @Mock
    ResponseManager cache;

    @Mock
    FingerprintPolicy fingerprintPolicy;

    /**
     * Built the way Spring Boot builds the application's mapper. The filter must answer the same document with
     * any other one, which is what {@code doFilter_whenGivenMapperCarriesNoProblemDetailMixin_*} checks.
     */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RecordingOperationStateChannel channel = new RecordingOperationStateChannel();
    private final MockHttpServletRequest request = request(BODY);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private OperationResponseCachingFilter tested;
    private RecordingLogAppender logAppender;
    private Level previousLogLevel;

    @BeforeEach
    void setUp() {
        tested = new OperationResponseCachingFilter(matcher, channel, fingerprintMatcher, keyExtractor, cache, mapper, clock);
    }

    @AfterEach
    void detachLogAppender() {
        if (logAppender != null) {
            filterLogger().detachAppender(logAppender);
            filterLogger().setLevel(previousLogLevel);
            logAppender = null;
        }
    }

    @Test
    @DisplayName("UT constructor when matcher is null should throw NullPointerException")
    void constructor_whenMatcherIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                null, channel, fingerprintMatcher, keyExtractor, cache, mapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("matcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor when channel is null should throw NullPointerException")
    void constructor_whenChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, null, fingerprintMatcher, keyExtractor, cache, mapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, channel, null, keyExtractor, cache, mapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor when keyExtractor is null should throw NullPointerException")
    void constructor_whenKeyExtractorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, channel, fingerprintMatcher, null, cache, mapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyExtractor cannot be null");
    }

    @Test
    @DisplayName("UT constructor when responseManager is null should throw NullPointerException")
    void constructor_whenCacheIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, channel, fingerprintMatcher, keyExtractor, null, mapper, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, channel, fingerprintMatcher, keyExtractor, cache, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new OperationResponseCachingFilter(
                matcher, channel, fingerprintMatcher, keyExtractor, cache, mapper, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT doFilter() when the pattern is not an idempotent one should pass the request down the chain untouched")
    void doFilter_whenUriIsNotIdempotentOne_shouldPassRequestDownChainUntouched() throws Exception {
        // given
        when(matcher.match(request)).thenReturn(null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(chain.request).isSameAs(request);
        assertThat(chain.response).isSameAs(response);
        verifyNoInteractions(keyExtractor, cache, fingerprintMatcher);
    }

    @Test
    @DisplayName("UT doFilter() when the metadata names no header should pass the request down the chain untouched")
    void doFilter_whenMetadataNamesNoHeader_shouldPassRequestDownChainUntouched() throws Exception {
        // given
        givenIdempotentUri(metadata().headerName(null).build());
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(chain.request).isSameAs(request);
        verifyNoInteractions(keyExtractor, cache);
    }

    @Test
    @DisplayName("UT doFilter() when the key comes from an expression should ignore the header the request carries")
    void doFilter_whenKeyComesFromExpression_shouldIgnoreHeaderRequestCarries() throws Exception {
        // given
        request.addHeader(HEADER_NAME, KEY.toString());
        givenIdempotentUri(metadata().headerName(null).build());
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        verifyNoInteractions(keyExtractor, cache);
    }

    @Test
    @DisplayName("UT doFilter() when the metadata header name is blank should pass the request down the chain untouched")
    void doFilter_whenMetadataHeaderNameIsBlank_shouldPassRequestDownChainUntouched() throws Exception {
        // given
        givenIdempotentUri(metadata().headerName("   ").build());
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(chain.request).isSameAs(request);
        verifyNoInteractions(keyExtractor, cache);
    }

    @Test
    @DisplayName("UT doFilter() when the idempotency key cannot be extracted should leave the request to the aspect")
    void doFilter_whenIdempotencyKeyCannotBeExtracted_shouldLeaveRequestToAspect() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        when(keyExtractor.extract(eq(HEADER_NAME), any(RequestContext.class)))
                .thenThrow(new IdempotencyKeyException("no key"));
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(chain.request).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("UT doFilter() when the key extractor fails on a fingerprinted endpoint should still hand the chain a re-readable request")
    void doFilter_whenKeyExtractorFailsOnFingerprintedEndpoint_shouldStillHandChainReReadableRequest() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        when(keyExtractor.extract(eq(HEADER_NAME), any(RequestContext.class)))
                .thenThrow(new IdempotencyKeyException("no key"));
        RecordingFilterChain chain = new RecordingFilterChain((req, res) ->
                assertThat(req.getInputStream().readAllBytes()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8)));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(chain.request).isInstanceOf(ContentCachingRequestWrapper.class);
        verifyNoInteractions(cache, fingerprintMatcher);
    }

    @Test
    @DisplayName("UT doFilter() when the key is extracted should read it from the current request context")
    void doFilter_whenKeyIsExtracted_shouldReadItFromCurrentRequestContext() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        ArgumentCaptor<RequestContext> context = ArgumentCaptor.forClass(RequestContext.class);
        verify(keyExtractor).extract(eq(HEADER_NAME), context.capture());
        assertThat(context.getValue()).isInstanceOf(HttpRequestContext.class);
        assertThat(context.getValue().getPath()).isEqualTo(URI);
        assertThat(context.getValue().getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("UT doFilter() when the response is in the cache should replay it without calling the chain")
    void doFilter_whenResponseIsInCache_shouldReplayItWithoutCallingChain() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenCached(new DefaultResponse(201, body, "application/json", null), null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentLength()).isEqualTo(body.length);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(cache, never()).save(any(), any());
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT doFilter() when the cached response has no body should replay it with zero content length")
    void doFilter_whenCachedResponseHasNoBody_shouldReplayItWithZeroContentLength() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenCached(new DefaultResponse(204, null, "application/json", null), null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentLength()).isZero();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("UT doFilter() when the cached response has no content type should replay it without one")
    void doFilter_whenCachedResponseHasNoContentType_shouldReplayItWithoutOne() throws Exception {
        // given
        byte [] body = "paid".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenCached(new DefaultResponse(200, body, null, null), null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
    }

    @Test
    @DisplayName("UT doFilter() when the cached response has a blank content type should replay it without one")
    void doFilter_whenCachedResponseHasBlankContentType_shouldReplayItWithoutOne() throws Exception {
        // given
        byte [] body = "paid".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenCached(new DefaultResponse(200, body, "   ", null), null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
    }

    @Test
    @DisplayName("UT doFilter() when the cache lookup throws should treat it as a miss and call the chain")
    void doFilter_whenCacheLookupThrows_shouldTreatItAsMissAndCallChain() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        when(cache.findByIdempotencyKey(eq(KEY)))
                .thenThrow(new IllegalStateException("cache is down"));
        RecordingFilterChain chain = respondingChain(201, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("UT doFilter() when the cache save throws should still deliver the response to the client")
    void doFilter_whenCacheSaveThrows_shouldStillDeliverResponseToClient() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenRecordedOperation();
        doThrowOnSave();
        RecordingFilterChain chain = respondingChain(201, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
    }

    @Test
    @DisplayName("UT doFilter() when the metadata uses fingerprint should hand the chain a request whose body stays readable")
    void doFilter_whenMetadataUsesFingerprint_shouldHandChainRequestWhoseBodyStaysReadable() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(FINGERPRINT);
        RecordingFilterChain chain = new RecordingFilterChain((req, res) -> {
            byte [] first = req.getInputStream().readAllBytes();
            byte [] second = req.getInputStream().readAllBytes();
            assertThat(first).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
            assertThat(second).isEqualTo(first);
        });

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.request).isInstanceOf(ContentCachingRequestWrapper.class);
    }

    @Test
    @DisplayName("UT doFilter() when the metadata does not use fingerprint should hand the chain the original request")
    void doFilter_whenMetadataDoesNotUseFingerprint_shouldHandChainOriginalRequest() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(false).build());
        givenExtractedKey();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.request).isSameAs(request);
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT doFilter() when the fingerprint is generated should compute it over the incoming request")
    void doFilter_whenFingerprintIsGenerated_shouldComputeItOverIncomingRequest() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(FINGERPRINT);

        // when
        tested.doFilter(request, response, new RecordingFilterChain());

        // then
        ArgumentCaptor<RequestContext> context = ArgumentCaptor.forClass(RequestContext.class);
        verify(fingerprintPolicy).generate(context.capture());
        assertThat(context.getValue().getPath()).isEqualTo(URI);
        assertThat(context.getValue().getBodyBytes()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("UT doFilter() when the generated fingerprint is null should answer 500 without calling the chain")
    void doFilter_whenGeneratedFingerprintIsNull_shouldAnswer500WithoutCallingChain() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(null);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");

        JsonNode problem = problem();
        assertThat(problem.get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
        assertThat(problem.get("status").asInt()).isEqualTo(500);
        assertThat(problem.get("instance").asText()).isEqualTo(URI);
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        assertThat(mapper.treeToValue(problem.get("timestamp"), Instant.class)).isEqualTo(NOW);
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the generated fingerprint is blank should answer 500 without calling the chain")
    void doFilter_whenGeneratedFingerprintIsBlank_shouldAnswer500WithoutCallingChain() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint("   ");
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(problem().get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("UT doFilter() when the given mapper carries no ProblemDetail mixin should still answer a flat problem")
    void doFilter_whenGivenMapperCarriesNoProblemDetailMixin_shouldStillAnswerFlatProblem() throws Exception {
        // given
        ObjectMapper bare = new ObjectMapper().registerModule(new JavaTimeModule());
        tested = new OperationResponseCachingFilter(matcher, channel, fingerprintMatcher, keyExtractor, cache, bare, clock);
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(null);

        // when
        tested.doFilter(request, response, new RecordingFilterChain());

        // then
        JsonNode problem = problem();
        assertThat(problem.get("properties")).isNull();
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        assertThat(problem.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("UT constructor when the given mapper carries no ProblemDetail mixin should not add it to that mapper")
    void constructor_whenGivenMapperCarriesNoProblemDetailMixin_shouldNotAddItToThatMapper() {
        // given
        ObjectMapper bare = new ObjectMapper();

        // when
        new OperationResponseCachingFilter(matcher, channel, fingerprintMatcher, keyExtractor, cache, bare, clock);

        // then
        assertThat(bare.getSerializationConfig().findMixInClassFor(ProblemDetail.class)).isNull();
    }

    @Test
    @DisplayName("UT doFilter() when the cached fingerprint matches should replay the cached response")
    void doFilter_whenCachedFingerprintMatches_shouldReplayCachedResponse() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(FINGERPRINT);
        givenCached(new DefaultResponse(201, body, "application/json", null), PREVIOUS_FINGERPRINT);
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(fingerprintMatcher).match(FINGERPRINT, PREVIOUS_FINGERPRINT, fingerprintPolicy, KEY);
    }

    @Test
    @DisplayName("UT doFilter() when the recorded response carried headers should replay them too")
    void doFilter_whenRecordedResponseCarriedHeaders_shouldReplayThemToo() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenRecordedOperation();
        tested.doFilter(request, response, headeredChain());

        MockHttpServletRequest retry = request(BODY);
        MockHttpServletResponse replay = new MockHttpServletResponse();
        when(matcher.match(retry)).thenReturn(metadata().build());
        Response recorded = savedResponse();
        assertThat(recorded.getHeaders())
                .describedAs("the record keeps the headers, so what a replay drops it drops on the way out")
                .containsEntry("Location", "/payments/1");
        givenCached(recorded, PREVIOUS_FINGERPRINT);

        // when
        tested.doFilter(retry, replay, new RecordingFilterChain());

        // then
        assertThat(replay.getHeader("Location")).isEqualTo("/payments/1");
        assertThat(replay.getHeader("ETag")).isEqualTo("\"v1\"");
    }

    @Test
    @DisplayName("UT doFilter() when the cached fingerprint does not match should answer 422 and replay nothing")
    void doFilter_whenCachedFingerprintDoesNotMatch_shouldAnswer422AndReplayNothing() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        givenGeneratedFingerprint(FINGERPRINT);
        givenCached(new DefaultResponse(201, body, "application/json", null), PREVIOUS_FINGERPRINT);
        givenFingerprintMismatch();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsByteArray()).isNotEqualTo(body);

        JsonNode problem = problem();
        assertThat(problem.get("type").asText()).isEqualTo(ProblemTypes.IDEMPOTENCY_KEY_REUSE.toString());
        assertThat(problem.get("status").asInt()).isEqualTo(422);
        assertThat(problem.get("instance").asText()).isEqualTo(URI);
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the chain writes a response should copy it to the real response and cache it")
    void doFilter_whenChainWritesResponse_shouldCopyItToRealResponseAndCacheIt() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(201, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);

        Response saved = savedResponse();
        assertThat(saved.getStatus()).isEqualTo(201);
        assertThat(saved.getBody()).isEqualTo(body);
        assertThat(saved.getContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("UT doFilter() when the fingerprint policy throws should answer 500 without calling the chain")
    void doFilter_whenFingerprintPolicyThrows_shouldAnswer500WithoutCallingChain() throws Exception {
        // given
        givenIdempotentUri(metadata().useFingerprint(true).fingerprintPolicy(fingerprintPolicy).build());
        givenExtractedKey();
        when(fingerprintPolicy.generate(any(RequestContext.class)))
                .thenThrow(new EmptyRequestBodyException("body is null or empty"));
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(chain.calls).isZero();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(problem().get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
        assertThat(problem().get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("UT doFilter() when the response is cached should let the entry die together with the recorded operation")
    void doFilter_whenResponseIsCached_shouldLetEntryDieTogetherWithRecordedOperation() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(201, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        verify(cache).save(eq(KEY), any(Response.class));
    }

    @Test
    @DisplayName("UT doFilter() when the operation was not recorded should store nothing and still answer the client")
    void doFilter_whenOperationWasNotRecorded_shouldStoreNothingAndStillAnswerClient() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        RecordingFilterChain chain = respondingChain(201, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the operation was replayed from the repository should store nothing")
    void doFilter_whenOperationWasReplayedFromRepository_shouldStoreNothing() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenReplayedOperation();
        RecordingFilterChain chain = respondingChain(201, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the chain throws should store nothing because no operation was recorded")
    void doFilter_whenChainThrows_shouldStoreNothingBecauseNoOperationWasRecorded() {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        RecordingFilterChain chain = new RecordingFilterChain((req, res) -> {
            throw new IllegalStateException("boom");
        });

        // when
        assertThatThrownBy(() -> tested.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class);

        // then
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the channel fails should still answer the client with the whole body")
    void doFilter_whenChannelFails_shouldStillAnswerClientWithWholeBody() throws Exception {
        // given
        byte [] body = "{\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        OperationResponseCachingFilter tested = new OperationResponseCachingFilter(
                matcher, new ThrowingOperationStateChannel(), fingerprintMatcher, keyExtractor, cache, mapper, clock);
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        RecordingFilterChain chain = respondingChain(201, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the response status belongs to no known class should store it as an ordinary one")
    void doFilter_whenResponseStatusBelongsToNoKnownClass_shouldStoreItAsOrdinaryOne() throws Exception {
        // given
        givenIdempotentUri(metadata().responseConfig(cacheConfig(false, false)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(600, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(savedResponse().getStatus()).isEqualTo(600);
    }

    @Test
    @DisplayName("UT doFilter() when the error classes are switched off should still record an ordinary answer")
    void doFilter_whenErrorClassesAreSwitchedOff_shouldStillRecordOrdinaryAnswer() throws Exception {
        // given - the flags govern which failed answers may be replayed, not whether the operation keeps one
        givenIdempotentUri(metadata().responseConfig(ResponseConfig.disabled()).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(savedResponse().getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("UT doFilter() when the response is 4xx and 4xx caching is off should not store it")
    void doFilter_whenResponseIs4xxAnd4xxCachingIsOff_shouldNotStoreIt() throws Exception {
        // given
        givenIdempotentUri(metadata().responseConfig(cacheConfig(false, false)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(400, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the response is 4xx and 4xx caching is on should store it")
    void doFilter_whenResponseIs4xxAnd4xxCachingIsOn_shouldStoreIt() throws Exception {
        // given
        byte [] body = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().responseConfig(cacheConfig(true, false)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(400, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        Response saved = savedResponse();
        assertThat(saved.getStatus()).isEqualTo(400);
        assertThat(saved.getBody()).isEqualTo(body);
    }

    @Test
    @DisplayName("UT doFilter() when the response is 5xx and 5xx caching is off should not store it")
    void doFilter_whenResponseIs5xxAnd5xxCachingIsOff_shouldNotStoreIt() throws Exception {
        // given
        givenIdempotentUri(metadata().responseConfig(cacheConfig(true, false)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(500, "application/json", "{}".getBytes(StandardCharsets.UTF_8));

        // when
        tested.doFilter(request, response, chain);

        // then
        verify(cache, never()).save(any(), any());
    }

    @Test
    @DisplayName("UT doFilter() when the response is 5xx and 5xx caching is on should store it")
    void doFilter_whenResponseIs5xxAnd5xxCachingIsOn_shouldStoreIt() throws Exception {
        // given
        byte [] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
        givenIdempotentUri(metadata().responseConfig(cacheConfig(false, true)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(500, "application/json", body);

        // when
        tested.doFilter(request, response, chain);

        // then
        Response saved = savedResponse();
        assertThat(saved.getStatus()).isEqualTo(500);
        assertThat(saved.getBody()).isEqualTo(body);
    }

    @Test
    @DisplayName("UT doFilter() when the response is 3xx should store it whatever the 4xx and 5xx switches say")
    void doFilter_whenResponseIs3xx_shouldStoreItWhateverSwitchesSay() throws Exception {
        // given
        givenIdempotentUri(metadata().responseConfig(cacheConfig(false, false)).build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = respondingChain(302, "application/json", new byte [0]);

        // when
        tested.doFilter(request, response, chain);

        // then
        assertThat(savedResponse().getStatus()).isEqualTo(302);
    }

    @Test
    @DisplayName("UT doFilter() when the chain leaves the status untouched should cache it as 200")
    void doFilter_whenChainLeavesStatusUntouched_shouldCacheItAs200() throws Exception {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        givenRecordedOperation();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        tested.doFilter(request, response, chain);

        // then
        Response saved = savedResponse();
        assertThat(saved.getStatus()).isEqualTo(200);
        assertThat(saved.getBody()).isEmpty();
    }

    @Test
    @DisplayName("UT doFilter() when the chain throws should let the exception reach the container")
    void doFilter_whenChainThrows_shouldLetExceptionReachContainer() {
        // given
        givenIdempotentUri(metadata().build());
        givenExtractedKey();
        RecordingFilterChain chain = new RecordingFilterChain((req, res) -> {
            throw new IllegalStateException("boom");
        });

        // when / then
        assertThatThrownBy(() -> tested.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    private void givenIdempotentUri(OperationMetadata metadata) {
        when(matcher.match(request)).thenReturn(metadata);
    }

    private void givenExtractedKey() {
        when(keyExtractor.extract(eq(HEADER_NAME), any(RequestContext.class))).thenReturn(KEY);
    }

    private void givenRecordedOperation() {
        channel.publish(TestOperationState.of(false));
    }

    private void recordFilterLog() {
        logAppender = new RecordingLogAppender();
        logAppender.start();
        previousLogLevel = filterLogger().getLevel();
        filterLogger().setLevel(Level.DEBUG);
        filterLogger().addAppender(logAppender);
    }

    private ch.qos.logback.classic.Logger filterLogger() {
        return (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperationResponseCachingFilter.class);
    }

    private void givenReplayedOperation() {
        channel.publish(TestOperationState.of(true));
    }

    private void givenGeneratedFingerprint(String fingerprint) {
        when(fingerprintPolicy.generate(any(RequestContext.class))).thenReturn(fingerprint);
    }

    private void givenCached(Response response, String fingerprint) {
        when(cache.findByIdempotencyKey(eq(KEY)))
                .thenReturn(Optional.of(new DefaultResponseContainer(response, fingerprint)));
    }

    private void givenFingerprintMismatch() {
        doThrow(new FingerprintMismatchException(null, "mismatch"))
                .when(fingerprintMatcher).match(any(), any(), any(), any());
    }

    private void doThrowOnSave() {
        doThrow(new IllegalStateException("cache is down"))
                .when(cache).save(any(), any());
    }

    private Response savedResponse() {
        ArgumentCaptor<Response> saved = ArgumentCaptor.forClass(Response.class);
        verify(cache).save(eq(KEY), saved.capture());
        return saved.getValue();
    }

    private JsonNode problem() throws IOException {
        return mapper.readTree(response.getContentAsByteArray());
    }

    private static TestOperationMetadata.Builder metadata() {
        return TestOperationMetadata.builder()
                .headerName(HEADER_NAME)
                .responseConfig(cacheConfig(true, true));
    }

    private static ResponseConfig cacheConfig(boolean shouldCache4xx, boolean shouldCache5xx) {
        return ResponseConfig.builder()
                .shouldCache4xx(shouldCache4xx)
                .shouldCache5xx(shouldCache5xx)
                .build();
    }

    private static MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(METHOD, URI);
        request.addHeader(HEADER_NAME, KEY.toString());
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }

    private static RecordingFilterChain headeredChain() {
        return new RecordingFilterChain((req, res) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) res;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("Location", "/payments/1");
            httpResponse.setHeader("ETag", "\"v1\"");
            httpResponse.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        });
    }

    private static RecordingFilterChain respondingChain(int status, String contentType, byte [] body) {
        return new RecordingFilterChain((req, res) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) res;
            httpResponse.setStatus(status);
            httpResponse.setContentType(contentType);
            httpResponse.getOutputStream().write(body);
        });
    }

    /**
     * Stands in for the core's half of the request: the operation manager publishes here what it actually
     * wrote to the repository, and the filter reads it back to decide whether there is anything worth caching.
     */
    /**
     * Collects what the filter logged, so that a branch taken on purpose can be told apart from one that only
     * looks harmless because the surrounding {@code catch} swallowed it.
     */
    private static final class RecordingLogAppender extends AppenderBase<ILoggingEvent> {

        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected synchronized void append(ILoggingEvent event) {
            events.add(event);
        }

        private synchronized List<ILoggingEvent> eventsAt(Level level) {
            return events.stream().filter(event -> level.equals(event.getLevel())).toList();
        }

        private synchronized List<String> messagesAt(Level level) {
            return eventsAt(level).stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
    }

    private static final class RecordingOperationStateChannel implements OperationStateChannel {

        private OperationState state;

        @Override
        public void publish(OperationState state) {
            this.state = state;
        }

        @Override
        public OperationState consume() {
            return state;
        }
    }

    /**
     * A channel that cannot answer at all - the way {@link HttpAttributesOperationStateChannel} behaves when
     * nothing bound the request attributes to the thread the filter finishes on.
     */
    private static final class ThrowingOperationStateChannel implements OperationStateChannel {

        @Override
        public void publish(OperationState state) {
            throw new IllegalStateException("No HTTP request is bound to the current thread");
        }

        @Override
        public OperationState consume() {
            throw new IllegalStateException("No HTTP request is bound to the current thread");
        }
    }

    /**
     * Remembers what the filter handed down the chain - the wrappers it puts on the request and the response
     * are only observable from here.
     */
    private static final class RecordingFilterChain implements FilterChain {

        private final ChainBehaviour behaviour;

        ServletRequest request;
        ServletResponse response;
        int calls;

        RecordingFilterChain() {
            this((req, res) -> { });
        }

        RecordingFilterChain(ChainBehaviour behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            calls++;
            this.request = request;
            this.response = response;
            behaviour.accept(request, response);
        }
    }

    @FunctionalInterface
    private interface ChainBehaviour {
        void accept(ServletRequest request, ServletResponse response) throws IOException, ServletException;
    }
}
