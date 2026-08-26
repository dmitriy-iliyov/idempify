package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletPath;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.RequestPath;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyHttpAutoConfigurationIntegrationTest {

    private final WebApplicationContextRunner contextRunner = contextRunnerWithout()
            .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
            .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
            .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
            .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("IT context when required beans exist should register every default bean of the module")
    void context_whenRequiredBeansExist_shouldRegisterEveryDefaultBeanOfModule() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KeyExtractor.class);
            assertThat(context).hasSingleBean(HttpKeyExtractor.class);

            assertThat(context).hasSingleBean(IdempotentHandlerRegistry.class);
            assertThat(context).hasSingleBean(DefaultIdempotentHandlerRegistry.class);

            assertThat(context).hasSingleBean(IdempotentRequestMatcher.class);
            assertThat(context).hasSingleBean(DefaultIdempotentRequestMatcher.class);

            assertThat(context).hasSingleBean(RequestContextProvider.class);
            assertThat(context).hasSingleBean(HttpRequestContextProvider.class);

            assertThat(context).hasSingleBean(OperationStateChannel.class);
            assertThat(context).hasSingleBean(HttpAttributesOperationStateChannel.class);

            assertThat(context).hasSingleBean(IdempifyControllerAdvice.class);
        });
    }

    @Test
    @DisplayName("IT context when no OperationMetadataResolver exists should fail to start")
    void context_whenNoOperationMetadataResolverExists_shouldFailToStart() {
        contextRunnerWithout()
                .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no ResponseCache exists should start without the caching filter")
    void context_whenNoResponseCacheExists_shouldStartWithoutCachingFilter() {
        contextRunnerWithout()
                .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("idempifyOperationResponseCachingFilterRegistrationBean");
                    assertThat(context).hasSingleBean(IdempotentRequestMatcher.class);
                });
    }

    @Test
    @DisplayName("IT context when no RequestMappingHandlerMapping exists should fail to start")
    void context_whenNoRequestMappingHandlerMappingExists_shouldFailToStart() {
        contextRunnerWithout()
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no FingerprintMatcher exists should fail to start")
    void context_whenNoFingerprintMatcherExists_shouldFailToStart() {
        contextRunnerWithout()
                .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no ObjectMapper exists should start anyway")
    void context_whenNoObjectMapperExists_shouldStartAnyway() {
        contextRunnerWithout()
                .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                });
    }

    @Test
    @DisplayName("IT context when several ObjectMapper beans exist and none is primary should start anyway")
    void context_whenSeveralObjectMapperBeansExistAndNoneIsPrimary_shouldStartAnyway() {
        contextRunner
                .withBean("anotherObjectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                });
    }

    @Test
    @DisplayName("IT context when no Clock exists should fail to start")
    void context_whenNoClockExists_shouldFailToStart() {
        contextRunnerWithout()
                .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when existing KeyExtractor bean should not register HttpKeyExtractor")
    void context_whenExistingKeyExtractorBean_shouldNotRegisterHttpKeyExtractor() {
        contextRunner
                .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(KeyExtractor.class);
                    assertThat(context).doesNotHaveBean(HttpKeyExtractor.class);
                });
    }

    @Test
    @DisplayName("IT context when existing IdempotentHandlerRegistry bean should not register the default one")
    void context_whenExistingIdempotentHandlerRegistry_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(IdempotentHandlerRegistry.class, () -> mock(IdempotentHandlerRegistry.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentHandlerRegistry.class);
                    assertThat(context).doesNotHaveBean(DefaultIdempotentHandlerRegistry.class);
                });
    }

    @Test
    @DisplayName("IT context when existing IdempotentRequestMatcher bean should not register the default one")
    void context_whenExistingIdempotentRequestMatcher_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(IdempotentRequestMatcher.class, () -> mock(IdempotentRequestMatcher.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentRequestMatcher.class);
                    assertThat(context).doesNotHaveBean(DefaultIdempotentRequestMatcher.class);
                });
    }

    @Test
    @DisplayName("IT context when existing OperationStateChannel bean should not register HttpAttributesOperationStateChannel")
    void context_whenExistingOperationStateChannelBean_shouldNotRegisterHttpAttributesOperationStateChannel() {
        contextRunner
                .withBean(OperationStateChannel.class, () -> mock(OperationStateChannel.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationStateChannel.class);
                    assertThat(context).doesNotHaveBean(HttpAttributesOperationStateChannel.class);
                });
    }

    @Test
    @DisplayName("IT context when existing RequestContextProvider bean should not register HttpRequestContextProvider")
    void context_whenExistingRequestContextProviderBean_shouldNotRegisterHttpRequestContextProvider() {
        contextRunner
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestContextProvider.class);
                    assertThat(context).doesNotHaveBean(HttpRequestContextProvider.class);
                });
    }

    @Test
    @DisplayName("IT context when existing IdempifyControllerAdvice bean should not register another one")
    void context_whenExistingIdempifyControllerAdviceBean_shouldNotRegisterAnotherOne() {
        contextRunner
                .withBean(IdempifyControllerAdvice.class, () -> new IdempifyControllerAdvice(Clock.systemUTC()))
                .run(context -> assertThat(context).hasSingleBean(IdempifyControllerAdvice.class));
    }

    @Test
    @DisplayName("IT context when no existing response caching filter should register it last for every request")
    void context_whenNoExistingResponseCachingFilter_shouldRegisterItLastForEveryRequest() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);

            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(OperationResponseCachingFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
    }

    @Test
    @DisplayName("IT context when a json message converter exists should write the filter problems with its mapper")
    void context_whenJsonMessageConverterExists_shouldWriteFilterProblemsWithItsMapper() {
        // given
        ObjectMapper converterMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        contextRunner
                .withBean(MappingJackson2HttpMessageConverter.class,
                        () -> new MappingJackson2HttpMessageConverter(converterMapper))
                .withBean(IdempotentRequestMatcher.class, () -> request -> brokenFingerprintMetadata())
                .run(context -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    // when
                    filterOf(context).doFilter(keyedRequest(), response, new MockFilterChain());

                    // then
                    assertThat(response.getStatus()).isEqualTo(500);
                    JsonNode timestamp = new ObjectMapper().readTree(response.getContentAsByteArray()).get("timestamp");
                    assertThat(timestamp.isNumber())
                            .describedAs("timestamp written as a number, the way the converter's mapper writes dates")
                            .isTrue();
                });
    }

    @Test
    @DisplayName("IT context when existing response caching filter bean should not register another one")
    void context_whenExistingResponseCachingFilterBean_shouldNotRegisterAnotherOne() {
        contextRunner
                .withUserConfiguration(ExistingFilterConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(FilterRegistrationBean.class));
    }

    @Test
    @DisplayName("IT context when the application is not a web one should not register anything")
    void context_whenApplicationIsNotWebOne_shouldNotRegisterAnything() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyHttpAutoConfiguration.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(HttpKeyExtractor.class);
                    assertThat(context).doesNotHaveBean(IdempotentHandlerRegistry.class);
                    assertThat(context).doesNotHaveBean(IdempotentRequestMatcher.class);
                    assertThat(context).doesNotHaveBean(HttpRequestContextProvider.class);
                    assertThat(context).doesNotHaveBean(HttpAttributesOperationStateChannel.class);
                    assertThat(context).doesNotHaveBean(IdempifyControllerAdvice.class);
                });
    }

    @Test
    @DisplayName("IT context when the mvc endpoints are scanned should register the idempotent ones only")
    void context_whenMvcEndpointsAreScanned_shouldRegisterIdempotentOnesOnly() {
        webMvcContextRunner()
                .run(context -> {
                    assertThat(registeredHandler(context, "POST", "/payments")).isNotNull();
                    assertThat(registeredHandler(context, "POST", "/refunds")).isNull();
                });
    }

    @Test
    @DisplayName("IT context when the mvc endpoints are scanned should register them under their own http method")
    void context_whenMvcEndpointsAreScanned_shouldRegisterThemUnderTheirOwnHttpMethod() {
        webMvcContextRunner()
                .run(context -> {
                    assertThat(registeredHandler(context, "PUT", "/payments")).isNull();
                    assertThat(registeredHandler(context, "GET", "/payments")).isNull();
                });
    }

    @Test
    @DisplayName("IT context when the mvc endpoints are scanned should register them as the application declares them")
    void context_whenMvcEndpointsAreScanned_shouldRegisterThemAsApplicationDeclaresThem() {
        webMvcContextRunner()
                .withBean(DispatcherServletPath.class, () -> () -> "/api")
                .run(context -> {
                    assertThat(registeredHandler(context, "POST", "/payments")).isNotNull();
                    assertThat(registeredHandler(context, "POST", "/api/payments")).isNull();
                });
    }

    private static WebApplicationContextRunner contextRunnerWithout() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyHttpAutoConfiguration.class));
    }

    private WebApplicationContextRunner webMvcContextRunner() {
        return contextRunnerWithout()
                .withUserConfiguration(WebMvcConfiguration.class)
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC);
    }

    private static Filter filterOf(AssertableWebApplicationContext context) {
        return context.getBean(FilterRegistrationBean.class).getFilter();
    }

    /**
     * Enough of a call site to make the filter answer a problem of its own: it asks for a fingerprint, and the
     * policy hands back a blank one.
     */
    private static OperationMetadata brokenFingerprintMetadata() {
        return TestOperationMetadata.builder()
                .headerName(IdempifyDefaults.HEADER_NAME)
                .useFingerprint(true)
                .fingerprintPolicy(new BlankFingerprintPolicy())
                .build();
    }

    private static final class BlankFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "";
        }

        @Override
        public boolean match(String previous, String current) {
            return false;
        }

        @Override
        public void handle(FingerprintMismatchContext context) { }
    }

    private static MockHttpServletRequest keyedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
        request.addHeader(IdempifyDefaults.HEADER_NAME, UUID.randomUUID().toString());
        return request;
    }

    private static HandlerMethod registeredHandler(AssertableWebApplicationContext context, String method, String path) {
        return context.getBean(IdempotentHandlerRegistry.class)
                .getHandlerMethod(method, RequestPath.parse(path, null));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebMvcConfiguration {

        @Bean
        public PaymentController paymentController() {
            return new PaymentController();
        }
    }

    @RestController
    static class PaymentController {

        @Idempotent
        @PostMapping("/payments")
        public String pay() {
            return "paid";
        }

        @PostMapping("/refunds")
        public String refund() {
            return "refunded";
        }
    }

    @Configuration
    static class ExistingFilterConfiguration {

        @Bean
        public FilterRegistrationBean<OperationResponseCachingFilter> existingResponseCachingFilter() {
            FilterRegistrationBean<OperationResponseCachingFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new OperationResponseCachingFilter(
                    mock(IdempotentRequestMatcher.class),
                    mock(OperationStateChannel.class),
                    mock(FingerprintMatcher.class),
                    mock(KeyExtractor.class),
                    mock(ResponseCache.class),
                    new ObjectMapper(),
                    Clock.systemUTC()
            ));
            return bean;
        }
    }
}
