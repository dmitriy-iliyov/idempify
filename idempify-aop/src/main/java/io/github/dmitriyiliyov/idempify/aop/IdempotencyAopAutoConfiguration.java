package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class IdempotencyAopAutoConfiguration {

    @Bean
    public IdempotentInterceptor idempotentInterceptor(KeyExtractor keyExtractor,
                                                       FingerprintManager fingerprintManager,
                                                       IdempotentProcessor processor) {
        return new DefaultIdempotentInterceptor(keyExtractor, fingerprintManager, processor);
    }

    @Bean
    public IdempotentAspect idempotentAspect(IdempotentInterceptor interceptor,
                                             RequestContextProvider requestContextProvider) {
        return new IdempotentAspect(interceptor, requestContextProvider);
    }
}
