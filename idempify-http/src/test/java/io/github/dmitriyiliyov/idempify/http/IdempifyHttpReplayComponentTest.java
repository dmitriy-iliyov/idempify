package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.aop.DefaultIdempotentInterceptor;
import io.github.dmitriyiliyov.idempify.aop.IdempotentAdvisor;
import io.github.dmitriyiliyov.idempify.aop.IdempotentInterceptor;
import io.github.dmitriyiliyov.idempify.aop.IdempotentOperationExpressionEvaluator;
import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.response.DefaultResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseManager;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one path that needs both entry points alive: a row that holds a result but no response. The filter has
 * nothing to replay from, so the request goes down the chain, and what answers it is the aspect returning the
 * stored result in place of the method body. That is the whole point of not rebuilding the response in the
 * core, and no arrangement with the aspect stubbed out can show it.
 * <p>
 * It lives beside {@link IdempifyHttpComponentTest} rather than inside it because the aspect proxies every
 * call site it advises, and that file reads its counters off the controller's own fields - which a proxy does
 * not carry. Here the count is asked for through a method, so it comes from the target.
 * <p>
 * Standing in for the core is the processor: it holds the row and decides replay, the way
 * {@code TransactionalIdempotentProcessor} over a repository would.
 */
class IdempifyHttpReplayComponentTest {

    private static final String HEADER_NAME = "Idempotency-Key";
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String BODY = "{\"amount\":10}";
    private static final String ANSWER = "{\"paid\":10}";
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private RowResponseManager responses;
    private ReplayingIdempotentProcessor core;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, CoreConfiguration.class, IdempifyHttpAutoConfiguration.class);
        context.refresh();

        responses = context.getBean(RowResponseManager.class);
        core = context.getBean(ReplayingIdempotentProcessor.class);
        controller = context.getBean(PaymentController.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(operationResponseFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("CT request when the key is new should reach the handler and leave both the result and the response behind")
    void request_whenKeyIsNew_shouldReachHandlerAndLeaveBothResultAndResponseBehind() throws Exception {
        // when
        mockMvc.perform(payment())
                .andExpect(status().isOk())
                .andExpect(content().json(ANSWER));

        // then
        assertThat(controller.payCalls()).isEqualTo(1);
        assertThat(core.storedResult).isEqualTo(ANSWER);
        assertThat(responses.storage).containsKey(KEY);
    }

    @Test
    @DisplayName("CT request when the row still holds its response should be answered by the filter without reaching the aspect")
    void request_whenRowStillHoldsItsResponse_shouldBeAnsweredByFilterWithoutReachingAspect() throws Exception {
        // given
        mockMvc.perform(payment());

        // when
        mockMvc.perform(payment())
                .andExpect(status().isOk())
                .andExpect(content().json(ANSWER));

        // then
        assertThat(controller.payCalls()).isEqualTo(1);
        assertThat(core.processCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the row lost its response should reach the handler and be answered by the aspect from the stored result")
    void request_whenRowLostItsResponse_shouldReachHandlerAndBeAnsweredByAspectFromStoredResult() throws Exception {
        // given - the operation completed, but writing its response did not
        mockMvc.perform(payment());
        responses.loseResponse(KEY);

        // when
        mockMvc.perform(payment())
                .andExpect(status().isOk())
                .andExpect(content().json(ANSWER));

        // then
        assertThat(controller.payCalls())
                .describedAs("the aspect short-circuits the call site, so the business method runs once and only once")
                .isEqualTo(1);
        assertThat(core.processCalls).isEqualTo(2);
    }

    private Filter operationResponseFilter() {
        return context.getBean(FilterRegistrationBean.class).getFilter();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder payment() {
        return post("/payments")
                .header(HEADER_NAME, KEY.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableAspectJAutoProxy
    static class WebMvcConfiguration implements WebMvcConfigurer {

        @Bean
        public PaymentController paymentController() {
            return new PaymentController();
        }

        @Bean
        public IdempotentInterceptor idempotentInterceptor(KeyExtractor keyExtractor, IdempotentProcessor processor) {
            return new DefaultIdempotentInterceptor(keyExtractor, processor);
        }

        @Bean
        public IdempotentAdvisor idempotentAdvisor(RequestContextProvider requestContextProvider,
                                                   OperationMetadataResolver metadataResolver,
                                                   IdempotentInterceptor interceptor) {
            return new IdempotentAdvisor(
                    new IdempotentOperationExpressionEvaluator(),
                    requestContextProvider,
                    metadataResolver,
                    interceptor
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CoreConfiguration {

        @Bean
        public RowResponseManager responseManager() {
            return new RowResponseManager();
        }

        @Bean
        public ReplayingIdempotentProcessor idempotentProcessor(ObjectProvider<OperationStateChannel> channel) {
            return new ReplayingIdempotentProcessor(channel);
        }

        @Bean
        public OperationMetadataResolver operationMetadataResolver() {
            return new DefaultOperationMetadataResolver(
                    new DefaultOperationMetadataCache(),
                    new HeaderKeyedOperationMetadataManager()
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

        private int payCalls;

        @Idempotent(headerName = HEADER_NAME)
        @PostMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
        public String pay(@RequestBody String body) {
            payCalls++;
            return ANSWER;
        }

        /**
         * Read through a method rather than off the field: the call site is advised, so what the test holds is
         * a proxy and only the target keeps the count.
         */
        int payCalls() {
            return payCalls;
        }
    }

    /**
     * Stands in for the core: the first call runs the method and keeps its result, every later one answers
     * from what it kept - which is what a repository row in {@code PROCESSED} does - and says so on the
     * channel, so the filter knows it is looking at a replay.
     */
    static class ReplayingIdempotentProcessor implements IdempotentProcessor {

        int processCalls;
        String storedResult;

        private final ObjectProvider<OperationStateChannel> channel;

        ReplayingIdempotentProcessor(ObjectProvider<OperationStateChannel> channel) {
            this.channel = channel;
        }

        @Override
        public Object process(OperationContext context, OperationMetadata metadata) {
            processCalls++;
            if (storedResult != null) {
                channel.getObject().publish(TestOperationState.of(true));
                return storedResult;
            }

            storedResult = (String) IdempotentProcessorUtils.getResult(context.getCallback());
            channel.getObject().publish(TestOperationState.of(false));
            return storedResult;
        }
    }

    /**
     * Stands in for the core manager over one row: the response can be taken away from it without the row
     * going too, which is the state this whole file is about.
     */
    static class RowResponseManager implements ResponseManager {

        final Map<UUID, Response> storage = new HashMap<>();

        void loseResponse(UUID idempotencyKey) {
            storage.remove(idempotencyKey);
        }

        @Override
        public Optional<ResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            Response stored = storage.get(idempotencyKey);
            return stored == null
                    ? Optional.empty()
                    : Optional.of(new DefaultResponseContainer(stored, null));
        }

        @Override
        public void save(UUID idempotencyKey, Response response) {
            storage.put(idempotencyKey, response);
        }
    }

    /**
     * Stands in for the core: every call site here is keyed by the same header and keeps the defaults.
     */
    static class HeaderKeyedOperationMetadataManager implements OperationMetadataManager {

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata) {
            return merge(metadata, null);
        }

        @Override
        public OperationMetadata merge(RawOperationMetadata metadata, String configName) {
            return TestOperationMetadata.builder()
                    .headerName(HEADER_NAME)
                    .ttl(Duration.ofHours(24))
                    .build();
        }
    }
}
