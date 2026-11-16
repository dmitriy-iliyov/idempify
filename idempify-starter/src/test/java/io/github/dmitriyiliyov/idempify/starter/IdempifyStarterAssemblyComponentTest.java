package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.aop.IdempifyAopAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.BlockingOperationRepository;
import io.github.dmitriyiliyov.idempify.core.NonBlockingOperationRepository;
import io.github.dmitriyiliyov.idempify.core.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyCoreAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.config.IdempotentEntry;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The starter as an application gets it: its own configuration together with the core and the entry it brings
 * along, so that what refuses to start is the assembled context, not one module judged alone.
 * <p>
 * The neighbouring modules an application adds by hand - the store, the serialization, the transport - only
 * answer here, and the transaction template is the application's own, the way Boot hands it over.
 */
class IdempifyStarterAssemblyComponentTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdempifyAutoConfiguration.class,
                    IdempifyCoreAutoConfiguration.class,
                    IdempifyAopAutoConfiguration.class
            ))
            .withBean(BlockingOperationRepository.class, () -> mock(BlockingOperationRepository.class))
            .withBean(NonBlockingOperationRepository.class, () -> mock(NonBlockingOperationRepository.class))
            .withBean(ResponseRepository.class, () -> mock(ResponseRepository.class))
            .withBean(ResultSerializer.class, () -> mock(ResultSerializer.class))
            .withBean(ResultDeserializer.class, () -> mock(ResultDeserializer.class))
            .withBean(ResponseSerializer.class, () -> mock(ResponseSerializer.class))
            .withBean(ResponseDeserializer.class, () -> mock(ResponseDeserializer.class))
            .withBean(OperationStateChannel.class, () -> mock(OperationStateChannel.class))
            .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
            .withBean(KeyExtractor.class, IdempifyStarterAssemblyComponentTest::httpKeyExtractor)
            .withBean(BodyCanonicalizerCreator.class, IdempifyStarterAssemblyComponentTest::jsonCanonicalizerCreator);

    @Test
    @DisplayName("CT application when AspectJ is on the classpath should start on the entry the starter brings")
    void application_whenAspectJIsOnClasspath_shouldStartOnEntryStarterBrings() {
        contextRunner
                .withBean(TransactionTemplate.class, IdempifyStarterAssemblyComponentTest::transactionTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotentEntry.class);
                });
    }

    @Test
    @DisplayName("CT application when AspectJ is missing from the classpath should refuse to start naming every way out")
    void application_whenAspectJIsMissingFromClasspath_shouldRefuseToStartNamingEveryWayOut() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Aspect.class))
                .withBean(TransactionTemplate.class, IdempifyStarterAssemblyComponentTest::transactionTemplate)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("No IdempotentEntry is registered")
                            .hasMessageContaining("idempify-aop")
                            .hasMessageContaining("an entry of your own")
                            .hasMessageContaining("idempify.enabled=false");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROPAGATION_REQUIRED", "PROPAGATION_REQUIRES_NEW", "PROPAGATION_NESTED"})
    @DisplayName("CT application when its transaction template opens a transaction of its own should start")
    void application_whenItsTransactionTemplateOpensTransactionOfItsOwn_shouldStart(String propagation) {
        contextRunner
                .withBean(TransactionTemplate.class, () -> transactionTemplate(propagation))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PROPAGATION_MANDATORY",
            "PROPAGATION_SUPPORTS",
            "PROPAGATION_NOT_SUPPORTED",
            "PROPAGATION_NEVER"
    })
    @DisplayName("CT application when its transaction template cannot open a transaction of its own should refuse to start")
    void application_whenItsTransactionTemplateCannotOpenTransactionOfItsOwn_shouldRefuseToStart(String propagation) {
        contextRunner
                .withBean(TransactionTemplate.class, () -> transactionTemplate(propagation))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("transactionTemplate must open a transaction of its own");
                });
    }

    private static TransactionTemplate transactionTemplate() {
        return transactionTemplate("PROPAGATION_REQUIRED");
    }

    private static TransactionTemplate transactionTemplate(String propagation) {
        TransactionTemplate template = new TransactionTemplate(mock(PlatformTransactionManager.class));
        template.setPropagationBehaviorName(propagation);
        return template;
    }

    private static KeyExtractor httpKeyExtractor() {
        KeyExtractor extractor = mock(KeyExtractor.class);
        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);
        return extractor;
    }

    private static BodyCanonicalizerCreator jsonCanonicalizerCreator() {
        BodyCanonicalizerCreator creator = mock(BodyCanonicalizerCreator.class);
        when(creator.getFormat()).thenReturn(BodyFormat.JSON);
        when(creator.create(any())).thenReturn(mock(BodyCanonicalizer.class));
        return creator;
    }
}
