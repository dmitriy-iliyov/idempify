package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataManager;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyAopAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
            .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
            .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
            .withBean(OperationMetadataManager.class, () -> mock(OperationMetadataManager.class))
            .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class));

    @Test
    @DisplayName("IT context when required beans exist should register every default bean of the module")
    void context_whenRequiredBeansExist_shouldRegisterEveryDefaultBeanOfModule() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotentInterceptor.class);
            assertThat(context).hasSingleBean(DefaultIdempotentInterceptor.class);

            assertThat(context).hasSingleBean(OperationMetadataCache.class);
            assertThat(context).hasSingleBean(DefaultOperationMetadataCache.class);

            assertThat(context).hasSingleBean(OperationMetadataFactory.class);
            assertThat(context).hasSingleBean(DefaultOperationMetadataFactory.class);

            assertThat(context).hasSingleBean(IdempotentOperationExpressionEvaluator.class);
            assertThat(context).hasSingleBean(IdempotentAspect.class);
        });
    }

    @Test
    @DisplayName("IT context when an IdempotentOperationExpressionEvaluator is already defined should back off")
    void context_whenIdempotentOperationExpressionEvaluatorIsAlreadyDefined_shouldBackOff() {
        IdempotentOperationExpressionEvaluator evaluator = new IdempotentOperationExpressionEvaluator();

        contextRunner
                .withBean(IdempotentOperationExpressionEvaluator.class, () -> evaluator)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentOperationExpressionEvaluator.class);
                    assertThat(context.getBean(IdempotentOperationExpressionEvaluator.class)).isSameAs(evaluator);
                });
    }

    @Test
    @DisplayName("IT context when an IdempotentInterceptor is already defined should back off")
    void context_whenIdempotentInterceptorIsAlreadyDefined_shouldBackOff() {
        contextRunner
                .withBean(IdempotentInterceptor.class, () -> mock(IdempotentInterceptor.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentInterceptor.class);
                    assertThat(context).doesNotHaveBean(DefaultIdempotentInterceptor.class);
                });
    }

    @Test
    @DisplayName("IT context when an OperationMetadataCache is already defined should back off")
    void context_whenOperationMetadataCacheIsAlreadyDefined_shouldBackOff() {
        contextRunner
                .withBean(OperationMetadataCache.class, () -> mock(OperationMetadataCache.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataCache.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMetadataCache.class);
                });
    }

    @Test
    @DisplayName("IT context when an OperationMetadataFactory is already defined should back off")
    void context_whenOperationMetadataFactoryIsAlreadyDefined_shouldBackOff() {
        contextRunner
                .withBean(OperationMetadataFactory.class, () -> mock(OperationMetadataFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataFactory.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMetadataFactory.class);
                });
    }

    @Test
    @DisplayName("IT context when an IdempotentAspect is already defined should back off")
    void context_whenIdempotentAspectIsAlreadyDefined_shouldBackOff() {
        contextRunner
                .withBean(IdempotentAspect.class, () -> mock(IdempotentAspect.class))
                .run(context -> assertThat(context).hasSingleBean(IdempotentAspect.class));
    }

    @Test
    @DisplayName("IT context when no KeyExtractor exists should fail to start")
    void context_whenNoKeyExtractorExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
                .withBean(OperationMetadataManager.class, () -> mock(OperationMetadataManager.class))
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no IdempotentProcessor exists should fail to start")
    void context_whenNoIdempotentProcessorExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
                .withBean(OperationMetadataManager.class, () -> mock(OperationMetadataManager.class))
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no OperationMetadataManager exists should fail to start")
    void context_whenNoOperationMetadataManagerExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
                .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no RequestContextProvider exists should fail to start")
    void context_whenNoRequestContextProviderExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
                .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
                .withBean(OperationMetadataManager.class, () -> mock(OperationMetadataManager.class))
                .run(context -> assertThat(context).hasFailed());
    }
}
