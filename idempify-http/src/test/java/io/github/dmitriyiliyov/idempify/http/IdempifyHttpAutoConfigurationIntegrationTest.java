package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyHttpAutoConfigurationIntegrationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyHttpAutoConfiguration.class))
            .withBean(RequestMappingHandlerMapping.class, () -> mock(RequestMappingHandlerMapping.class));

    @Test
    @DisplayName("IT context when no existing KeyExtractor bean should register HttpKeyExtractor")
    void context_whenNoExistingKeyExtractorBean_shouldRegisterHttpKeyExtractor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KeyExtractor.class);
            assertThat(context).hasSingleBean(HttpKeyExtractor.class);
        });
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
    @DisplayName("IT context when no existing IdempotentEndpointRegistry should register DefaultIdempotentEndpointRegistry")
    void context_whenNoExistingIdempotentEndpointRegistry_shouldRegisterDefaultIdempotentEndpointRegistry() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotentEndpointRegistry.class);
            assertThat(context).hasSingleBean(DefaultIdempotentEndpointRegistry.class);
        });
    }

    @Test
    @DisplayName("IT context when existing IdempotentEndpointRegistry bean should not register DefaultIdempotentEndpointRegistry")
    void context_whenExistingIdempotentEndpointRegistry_shouldNotRegisterDefaultIdempotentEndpointRegistry() {
        contextRunner
                .withBean(IdempotentEndpointRegistry.class, () -> mock(IdempotentEndpointRegistry.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentEndpointRegistry.class);
                    assertThat(context).doesNotHaveBean(DefaultIdempotentEndpointRegistry.class);
                });
    }

    @Test
    @DisplayName("IT context when no existing CachingHttpRequestBodyFilter should register filter with highest precedence")
    void context_whenNoExistingCachingHttpRequestBodyFilter_shouldRegisterFilterWithHighestPrecedence() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);

            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(registration.getFilter()).isInstanceOf(CachingHttpRequestBodyFilter.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
    }

    @Test
    @DisplayName("IT context when existing CachingHttpRequestBodyFilter bean should not register another filter")
    void context_whenExistingCachingHttpRequestBodyFilter_shouldNotRegisterAnotherFilter() {
        contextRunner
                .withUserConfiguration(ExistingCachingFilterConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(FilterRegistrationBean.class));
    }

    @Configuration
    static class ExistingCachingFilterConfiguration {

        @Bean
        public FilterRegistrationBean<CachingHttpRequestBodyFilter> existingCachingFilter() {
            FilterRegistrationBean<CachingHttpRequestBodyFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new CachingHttpRequestBodyFilter(mock(IdempotentEndpointRegistry.class)));
            return bean;
        }
    }
}