package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencyAopAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyAopAutoConfiguration.class))
            .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
            .withBean(FingerprintManager.class, () -> mock(FingerprintManager.class))
            .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
            .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class));

    @Test
    @DisplayName("IT context when required beans exist should register IdempotentInterceptor and IdempotentAspect")
    void context_whenRequiredBeansExist_shouldRegisterInterceptorAndAspect() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotentInterceptor.class);
            assertThat(context).hasSingleBean(DefaultIdempotentInterceptor.class);

            assertThat(context).hasSingleBean(IdempotentAspect.class);
        });
    }
}
