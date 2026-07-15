package io.github.dmitriyiliyov.springidempotency.aop;

import io.github.dmitriyiliyov.springidempotency.core.IdempotentProcessor;
import io.github.dmitriyiliyov.springidempotency.core.KeyExtractor;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintManager;
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
    public IdempotentAspect idempotentAspect(IdempotentInterceptor interceptor) {
        return new IdempotentAspect(interceptor);
    }
}
