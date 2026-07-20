package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@AutoConfiguration
@ConditionalOnWebApplication
public class IdempifyHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyExtractor idempifyHttpKeyExtractor() {
        return new HttpKeyExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentEndpointRegistry idempotentEndpointRegistry(RequestMappingHandlerMapping handlerMapping) {
        return new DefaultIdempotentEndpointRegistry(handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CachingHttpRequestBodyFilter> idempifyCachingHttpRequestBodyFilter(
            IdempotentEndpointRegistry endpointRegistry
    ) {
        FilterRegistrationBean<CachingHttpRequestBodyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CachingHttpRequestBodyFilter(endpointRegistry));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestContextProvider idempifyHttpRequestContextProvider() {
        return new HttpRequestContextProvider();
    }
}
