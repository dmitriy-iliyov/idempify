package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyAopAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
            .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
            .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
            .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
            .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class));

    @Test
    @DisplayName("IT context when required beans exist should register every default bean of the module")
    void context_whenRequiredBeansExist_shouldRegisterEveryDefaultBeanOfModule() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotentInterceptor.class);
            assertThat(context).hasSingleBean(DefaultIdempotentInterceptor.class);

            assertThat(context).hasSingleBean(IdempotentOperationExpressionEvaluator.class);
            assertThat(context).hasSingleBean(IdempotentAdvisor.class);
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
    @DisplayName("IT context when an IdempotentAdvisor is already defined should back off")
    void context_whenIdempotentAdvisorIsAlreadyDefined_shouldBackOff() {
        contextRunner
                .withBean(IdempotentAdvisor.class, () -> mock(IdempotentAdvisor.class))
                .run(context -> assertThat(context).hasSingleBean(IdempotentAdvisor.class));
    }

    @Test
    @DisplayName("IT context when AspectJ is missing from the classpath should not register anything")
    void context_whenAspectJIsMissingFromClasspath_shouldNotRegisterAnything() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Aspect.class, ProceedingJoinPoint.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempifyAopAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(IdempotentInterceptor.class);
                    assertThat(context).doesNotHaveBean(IdempotentOperationExpressionEvaluator.class);
                });
    }

    @Test
    @DisplayName("IT auto-configuration when the module is on the classpath should be listed as an import candidate")
    void autoConfiguration_whenModuleIsOnClasspath_shouldBeListedAsImportCandidate() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(IdempifyAopAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("IT context when no KeyExtractor exists should fail to start")
    void context_whenNoKeyExtractorExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(IdempotentProcessor.class, () -> mock(IdempotentProcessor.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no IdempotentProcessor exists should fail to start")
    void context_whenNoIdempotentProcessorExists_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyAopAutoConfiguration.class))
                .withBean(KeyExtractor.class, () -> mock(KeyExtractor.class))
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no OperationMetadataResolver exists should fail to start")
    void context_whenNoOperationMetadataResolverExists_shouldFailToStart() {
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
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotentInterceptor.class);
                    assertThat(context).doesNotHaveBean(IdempotentAdvisor.class);
                    assertThat(context).doesNotHaveBean(IdempotentOperationExpressionEvaluator.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotentInterceptor.class);
                    assertThat(context).hasSingleBean(IdempotentAdvisor.class);
                });
    }
}
