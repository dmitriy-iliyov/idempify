package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnWebApplication
public class IdempifyHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeyExtractor idempifyHttpKeyExtractor() {
        return new HttpKeyExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationStateChannel idempifyHttpAttributesOperationStateChannel() {
        return new HttpAttributesOperationStateChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentHandlerRegistry idempifyIdempotentHandlerRegistry(RequestMappingHandlerMapping handlerMapping) {
        return new DefaultIdempotentHandlerRegistry(handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentRequestMatcher idempifyIdempotentRequestMatcher(IdempotentHandlerRegistry handlerRegistry,
                                                                     OperationMetadataResolver metadataResolver) {
        return new DefaultIdempotentRequestMatcher(handlerRegistry, metadataResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ResponseCache.class)
    public FilterRegistrationBean<OperationResponseCachingFilter> idempifyOperationResponseCachingFilterRegistrationBean(
            IdempotentRequestMatcher matcher,
            OperationStateChannel channel,
            FingerprintMatcher fingerprintMatcher,
            KeyExtractor keyExtractor,
            ResponseCache cache,
            ObjectProvider<MappingJackson2HttpMessageConverter> jsonConverter,
            Clock clock
    ) {
        FilterRegistrationBean<OperationResponseCachingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(
                new OperationResponseCachingFilter(matcher, channel, fingerprintMatcher, keyExtractor, cache,
                        problemMapper(jsonConverter), clock)
        );
        registrationBean.setOrder(Ordered.LOWEST_PRECEDENCE);
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    private ObjectMapper problemMapper(ObjectProvider<MappingJackson2HttpMessageConverter> jsonConverter) {
        MappingJackson2HttpMessageConverter converter = jsonConverter.getIfUnique();
        return converter != null ? converter.getObjectMapper() : Jackson2ObjectMapperBuilder.json().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestContextProvider idempifyHttpRequestContextProvider() {
        return new HttpRequestContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempifyControllerAdvice idempifyControllerAdvice(Clock clock) {
        return new IdempifyControllerAdvice(clock);
    }
}
