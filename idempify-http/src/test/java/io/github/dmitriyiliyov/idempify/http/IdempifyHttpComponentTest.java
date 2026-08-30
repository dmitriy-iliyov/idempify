package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.IdempotencyConflictException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.RawHashingFingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.ThrowingEmptyBodyFallback;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.OperationState;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.server.RequestPath;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Drives the module as it is wired by its own auto-configuration: a real {@code RequestMappingHandlerMapping}
 * behind a real registry, matcher, key extractor and filter, in front of a real {@code DispatcherServlet}.
 * <p>
 * Only what belongs to the core is stood in for - the {@code ResponseCache} that has no implementation yet and
 * the {@code OperationMetadataManager} that decides the effective settings of a call site.
 */
class IdempifyHttpComponentTest {

    private static final String HEADER_NAME = IdempifyDefaults.HEADER_NAME;
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String BODY = "{\"amount\":10}";
    private static final String OTHER_BODY = "{\"amount\":9000}";
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    /**
     * How long the record of a completed operation lives in the repository. A cache entry must die together
     * with the record it stands for, so this is the value the filter has to derive its own ttl from.
     */
    private static final Duration OPERATION_TTL = Duration.ofHours(24);

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private RecordingResponseCache cache;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, CoreConfiguration.class, IdempifyHttpAutoConfiguration.class);
        context.refresh();

        cache = context.getBean(RecordingResponseCache.class);
        controller = context.getBean(PaymentController.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(responseCachingFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("CT request when the endpoint is idempotent and the key is new should reach the handler and cache its response")
    void request_whenEndpointIsIdempotentAndKeyIsNew_shouldReachHandlerAndCacheItsResponse() throws Exception {
        // when
        mockMvc.perform(payment(KEY))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"paid\":10}"));

        // then
        assertThat(controller.payCalls).isEqualTo(1);
        assertThat(cache.storage).containsKey(KEY);
        assertThat(cache.lastTtl).isEqualTo(OPERATION_TTL);
    }

    @Test
    @DisplayName("CT request when the same key comes back should be answered from the cache without reaching the handler")
    void request_whenSameKeyComesBack_shouldBeAnsweredFromCacheWithoutReachingHandler() throws Exception {
        // given
        mockMvc.perform(payment(KEY));

        // when
        mockMvc.perform(payment(KEY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"paid\":10}"));

        // then
        assertThat(controller.payCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when another key comes should reach the handler again")
    void request_whenAnotherKeyComes_shouldReachHandlerAgain() throws Exception {
        // given
        mockMvc.perform(payment(KEY));

        // when
        mockMvc.perform(payment(UUID.randomUUID())).andExpect(status().isCreated());

        // then
        assertThat(controller.payCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the endpoint is not idempotent should never be cached")
    void request_whenEndpointIsNotIdempotent_shouldNeverBeCached() throws Exception {
        // when
        mockMvc.perform(post("/refunds").header(HEADER_NAME, KEY.toString())).andExpect(status().isOk());
        mockMvc.perform(post("/refunds").header(HEADER_NAME, KEY.toString())).andExpect(status().isOk());

        // then
        assertThat(controller.refundCalls).isEqualTo(2);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the call site takes its key from an expression should reach the handler however often the same header comes")
    void request_whenCallSiteTakesKeyFromExpression_shouldReachHandlerHoweverOftenSameHeaderComes() throws Exception {
        // when
        mockMvc.perform(post("/payments/1/by-expression").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/payments/2/by-expression").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk());

        // then
        assertThat(controller.expressionKeyedCalls)
                .describedAs("the key of the operation is the path variable, so one header must not replay another call")
                .isEqualTo(2);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the idempotency key header is missing should be left to the aspect and not cached")
    void request_whenIdempotencyKeyHeaderIsMissing_shouldBeLeftToAspectAndNotCached() throws Exception {
        // when
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        // then
        assertThat(controller.payCalls).isEqualTo(2);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the idempotency key is not a uuid should be left to the aspect and not cached")
    void request_whenIdempotencyKeyIsNotUuid_shouldBeLeftToAspectAndNotCached() throws Exception {
        // when
        mockMvc.perform(post("/payments")
                        .header(HEADER_NAME, "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());

        // then
        assertThat(controller.payCalls).isEqualTo(1);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the endpoint pattern is templated should be matched and cached under its key")
    void request_whenEndpointPatternIsTemplated_shouldBeMatchedAndCachedUnderItsKey() throws Exception {
        // given
        mockMvc.perform(post("/orders/42/pay").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("paid:42"));

        // when
        mockMvc.perform(post("/orders/42/pay").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("paid:42"));

        // then
        assertThat(controller.orderPayCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the endpoint uses fingerprint should still hand the body to the handler")
    void request_whenEndpointUsesFingerprint_shouldStillHandBodyToHandler() throws Exception {
        // when
        mockMvc.perform(payment(KEY)).andExpect(status().isCreated());

        // then
        assertThat(controller.lastPayBody).isEqualTo(BODY);
    }

    @Test
    @DisplayName("CT request when the handler answers 4xx and 4xx caching is off should reach the handler every time")
    void request_whenHandlerAnswers4xxAnd4xxCachingIsOff_shouldReachHandlerEveryTime() throws Exception {
        // given
        mockMvc.perform(post("/payments/rejected").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isBadRequest());

        // when
        mockMvc.perform(post("/payments/rejected").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isBadRequest());

        // then
        assertThat(controller.rejectedCalls).isEqualTo(2);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when caching is disabled for the endpoint should reach the handler every time")
    void request_whenCachingIsDisabledForEndpoint_shouldReachHandlerEveryTime() throws Exception {
        // given
        mockMvc.perform(post("/payments/uncached").header(HEADER_NAME, KEY.toString())).andExpect(status().isOk());

        // when
        mockMvc.perform(post("/payments/uncached").header(HEADER_NAME, KEY.toString())).andExpect(status().isOk());

        // then
        assertThat(controller.uncachedCalls).isEqualTo(2);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the same key comes back with another body should be rejected as key reuse")
    void request_whenSameKeyComesBackWithAnotherBody_shouldBeRejectedAsKeyReuse() throws Exception {
        // given
        mockMvc.perform(payment(KEY)).andExpect(status().isCreated());

        // when
        mockMvc.perform(post("/payments")
                        .header(HEADER_NAME, KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OTHER_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.IDEMPOTENCY_KEY_REUSE.toString()))
                .andExpect(jsonPath("$.instance").value("/payments"))
                .andExpect(jsonPath("$.idempotencyKey").value(KEY.toString()));

        // then
        assertThat(controller.payCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the same key comes back with the same body should be replayed and not rejected")
    void request_whenSameKeyComesBackWithSameBody_shouldBeReplayedAndNotRejected() throws Exception {
        // given
        mockMvc.perform(payment(KEY)).andExpect(status().isCreated());

        // when
        mockMvc.perform(payment(KEY))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"paid\":10}"));

        // then
        assertThat(controller.payCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the handler reports a conflict should not leave that answer in the cache")
    void request_whenHandlerReportsConflict_shouldNotLeaveThatAnswerInCache() throws Exception {
        // given
        mockMvc.perform(post("/payments/conflicting").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isConflict());

        // when
        mockMvc.perform(post("/payments/conflicting").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isConflict());

        // then
        assertThat(cache.storage).isEmpty();
        assertThat(controller.conflictingCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the handler throws should leave nothing in the cache for the next attempt")
    void request_whenHandlerThrows_shouldLeaveNothingInCacheForNextAttempt() {
        // when / then
        assertThatThrownBy(() -> mockMvc.perform(post("/payments/broken").header(HEADER_NAME, KEY.toString())))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the core replays a recorded operation should not copy that answer into the cache")
    void request_whenCoreReplaysRecordedOperation_shouldNotCopyThatAnswerIntoCache() throws Exception {
        // when
        mockMvc.perform(post("/payments/replayed").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("replayed"));

        // then
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the handler reports a conflict should be answered by the advice as a problem")
    void request_whenHandlerReportsConflict_shouldBeAnsweredByAdviceAsProblem() throws Exception {
        // when / then
        mockMvc.perform(post("/payments/conflicting").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.OPERATION_IN_PROCESS.toString()))
                .andExpect(jsonPath("$.instance").value("/payments/conflicting"));
    }

    @Test
    @DisplayName("CT request when the fingerprint policy rejects the request should be answered by the filter before the servlet")
    void request_whenFingerprintPolicyRejectsRequest_shouldBeAnsweredByFilterBeforeServlet() throws Exception {
        // when / then
        mockMvc.perform(post("/payments").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString()))
                .andExpect(jsonPath("$.instance").value("/payments"))
                .andExpect(jsonPath("$.idempotencyKey").value(KEY.toString()));

        assertThat(controller.payCalls).isZero();
        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the core rejects the idempotency key should be answered by the advice as a problem")
    void request_whenCoreRejectsIdempotencyKey_shouldBeAnsweredByAdviceAsProblem() throws Exception {
        // when / then
        mockMvc.perform(post("/payments/unkeyed").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_IDEMPOTENCY_KEY.toString()))
                .andExpect(jsonPath("$.instance").value("/payments/unkeyed"));

        assertThat(cache.storage).isEmpty();
    }

    @Test
    @DisplayName("CT request when the core asks for the request context should get the one of the current request")
    void request_whenCoreAsksForRequestContext_shouldGetOneOfCurrentRequest() throws Exception {
        // when / then
        mockMvc.perform(post("/refunds").header(HEADER_NAME, KEY.toString()))
                .andExpect(status().isOk());

        assertThat(controller.lastRefundContext.getRequestType()).isEqualTo(RequestType.HTTP);
        assertThat(controller.lastRefundContext.getMethod()).isEqualTo("POST");
        assertThat(controller.lastRefundContext.getPath()).isEqualTo("/refunds");
        assertThat(controller.lastRefundContext.getHeader(HEADER_NAME)).isEqualTo(KEY.toString());
    }

    @Test
    @DisplayName("CT context when the module is auto-configured should put the response caching filter in front of the servlet")
    void context_whenModuleIsAutoConfigured_shouldPutResponseCachingFilterInFrontOfServlet() {
        assertThat(responseCachingFilter()).isInstanceOf(OperationResponseCachingFilter.class);

        IdempotentHandlerRegistry registry = context.getBean(IdempotentHandlerRegistry.class);
        assertThat(List.of("/payments", "/orders/42/pay", "/payments/rejected", "/payments/uncached",
                        "/payments/conflicting", "/payments/broken", "/payments/replayed", "/payments/unkeyed"))
                .allSatisfy(path -> assertThat(registry.getHandlerMethod("POST", RequestPath.parse(path, null)))
                        .isNotNull());
        assertThat(registry.getHandlerMethod("POST", RequestPath.parse("/refunds", null))).isNull();
        assertThat(registry.getHandlerMethod("PUT", RequestPath.parse("/payments", null))).isNull();
    }

    private Filter responseCachingFilter() {
        return context.getBean(FilterRegistrationBean.class).getFilter();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder payment(UUID key) {
        return post("/payments")
                .header(HEADER_NAME, key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebMvcConfiguration implements WebMvcConfigurer {

        private final ObjectProvider<OperationStateChannel> channel;
        private final ObjectProvider<RequestContextProvider> contextProvider;

        WebMvcConfiguration(ObjectProvider<OperationStateChannel> channel,
                            ObjectProvider<RequestContextProvider> contextProvider) {
            this.channel = channel;
            this.contextProvider = contextProvider;
        }

        @Bean
        public PaymentController paymentController() {
            return new PaymentController(channel, contextProvider);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new StubIdempotentCore(channel));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreConfiguration {

        @Bean
        public RecordingResponseCache responseCache() {
            return new RecordingResponseCache();
        }

        @Bean
        public OperationMetadataResolver operationMetadataResolver() {
            return new DefaultOperationMetadataResolver(
                    new DefaultOperationMetadataCache(),
                    new AnnotationDrivenOperationMetadataManager()
            );
        }

        @Bean
        public FingerprintMatcher fingerprintMatcher() {
            return new DefaultFingerprintMatcher(IdempotencyEventListener.NOOP);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json().build();
        }

        @Bean
        public Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @RestController
    static class PaymentController {

        int payCalls;
        int refundCalls;
        int orderPayCalls;
        int rejectedCalls;
        int uncachedCalls;
        int conflictingCalls;
        int expressionKeyedCalls;
        String lastPayBody;
        RequestContext lastRefundContext;

        private final ObjectProvider<OperationStateChannel> channel;
        private final ObjectProvider<RequestContextProvider> contextProvider;

        PaymentController(ObjectProvider<OperationStateChannel> channel,
                          ObjectProvider<RequestContextProvider> contextProvider) {
            this.channel = channel;
            this.contextProvider = contextProvider;
        }

        @Idempotent(headerName = HEADER_NAME, useFingerprint = Toggle.ENABLE)
        @PostMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> pay(@RequestBody String body) {
            payCalls++;
            lastPayBody = body;
            return ResponseEntity.status(201).body("{\"paid\":10}");
        }

        @Idempotent(headerName = HEADER_NAME)
        @PostMapping("/orders/{id}/pay")
        public String payOrder(@PathVariable String id) {
            orderPayCalls++;
            return "paid:" + id;
        }

        @Idempotent(idempotencyKey = "#id")
        @PostMapping("/payments/{id}/by-expression")
        public String payByExpression(@PathVariable String id) {
            expressionKeyedCalls++;
            return "paid:" + id;
        }

        @Idempotent(headerName = HEADER_NAME, cache4xx = Toggle.DISABLE)
        @PostMapping("/payments/rejected")
        public ResponseEntity<String> rejected() {
            rejectedCalls++;
            return ResponseEntity.badRequest().body("rejected");
        }

        @Idempotent(headerName = HEADER_NAME, useCache = Toggle.DISABLE)
        @PostMapping("/payments/uncached")
        public String uncached() {
            uncachedCalls++;
            return "uncached";
        }

        @Idempotent(headerName = HEADER_NAME)
        @PostMapping("/payments/conflicting")
        public String conflicting() {
            conflictingCalls++;
            throw new IdempotencyConflictException("another request is already processing this key");
        }

        @Idempotent(headerName = HEADER_NAME)
        @PostMapping("/payments/broken")
        public String broken() {
            throw new IllegalStateException("the business operation blew up");
        }

        /**
         * Stands for the case the core answers from the repository: the operation was recorded by an earlier
         * request, so what leaves the handler is a replay and not a result worth copying into the cache.
         */
        @Idempotent(headerName = HEADER_NAME)
        @PostMapping("/payments/replayed")
        public String replayed() {
            channel.getObject().publish(TestOperationState.of(NOW.plus(OPERATION_TTL), true));
            return "replayed";
        }

        @Idempotent(headerName = HEADER_NAME)
        @PostMapping("/payments/unkeyed")
        public String unkeyed() {
            throw new IdempotencyKeyException("HTTP header %s is null or empty".formatted(HEADER_NAME));
        }

        @PostMapping("/refunds")
        public String refund() {
            refundCalls++;
            lastRefundContext = contextProvider.getObject().getContext();
            return "refunded";
        }
    }

    /**
     * Stands in for the core: turns what a call site declares into effective settings, the way an
     * {@code OperationMetadataManager} implementation would.
     */
    static class AnnotationDrivenOperationMetadataManager implements OperationMetadataManager {

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata) {
            return merge(metadata, null);
        }

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata, String configName) {
            return TestOperationMetadata.builder()
                    .headerName(headerNameOf(metadata))
                    .ttl(metadata.getTtl() == null ? OPERATION_TTL : metadata.getTtl())
                    .useFingerprint(metadata.getFingerprintToggle() == Toggle.ENABLE)
                    .fingerprintPolicy(new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback()))
                    .responseCacheConfig(ResponseCacheConfig.builder()
                            .enabled(metadata.getCacheToggle() != Toggle.DISABLE)
                            .shouldCache4xx(metadata.getCache4xxToggle() != Toggle.DISABLE)
                            .shouldCache5xx(metadata.getCache5xxToggle() == Toggle.ENABLE)
                            .build())
                    .build();
        }

        /**
         * A call site taking its key from an expression resolves to metadata without a header name, exactly as
         * the real manager leaves it - that is what keeps the filter from reading a key the operation is not
         * recorded under.
         */
        private String headerNameOf(RawOperationMetadata metadata) {
            if (!metadata.useHeaderName()) {
                return null;
            }
            return metadata.getHeaderName() == null ? HEADER_NAME : metadata.getHeaderName();
        }
    }

    /**
     * Stands in for the aspect and the operation manager of the core: it is their job to tell the module,
     * through the {@code OperationStateChannel}, that the repository now holds a record of this operation and
     * until when. Only what the filter reads back matters here, so the stand-in publishes on the same occasion
     * the real manager does - after the handler returned - and stays silent when it threw.
     */
    static class StubIdempotentCore implements HandlerInterceptor {

        private final ObjectProvider<OperationStateChannel> channel;

        StubIdempotentCore(ObjectProvider<OperationStateChannel> channel) {
            this.channel = channel;
        }

        @Override
        public void postHandle(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               ModelAndView modelAndView) {
            if (!(handler instanceof HandlerMethod handlerMethod)
                    || !handlerMethod.hasMethodAnnotation(Idempotent.class)
                    || request.getHeader(HEADER_NAME) == null) {
                return;
            }
            OperationStateChannel operationStateChannel = channel.getObject();
            OperationState alreadyPublished = operationStateChannel.consume();
            operationStateChannel.publish(alreadyPublished != null
                    ? alreadyPublished
                    : TestOperationState.of(NOW.plus(OPERATION_TTL), false));
        }
    }

    static class RecordingResponseCache implements ResponseCache {

        final Map<UUID, CachedResponse> storage = new ConcurrentHashMap<>();
        Duration lastTtl;

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            return storage.get(idempotencyKey);
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
            lastTtl = ttl;
            storage.put(idempotencyKey, response);
        }
    }
}
