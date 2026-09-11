package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks what only a context can answer: that the module contributes both hooks once it is asked for, that
 * either of them steps aside for one the application wrote, and that each of the three ways of not asking -
 * {@code idempify.metrics.enabled} left out or set to {@code false}, {@code idempify.enabled} switched off,
 * micrometer absent from the classpath - keeps the whole module out.
 */
class IdempifyMetricsAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = module()
            .withPropertyValues("idempify.metrics.enabled=true")
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    @DisplayName("IT autoConfiguration should be registered so that an application only adds the dependency")
    void autoConfiguration_shouldBeRegisteredSoThatApplicationOnlyAddsDependency() {
        // when
        Iterable<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        // then
        assertThat(candidates).contains(IdempifyMetricsAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("IT context when metrics are asked for should register every default bean of the module")
    void context_whenMetricsAreAskedFor_shouldRegisterEveryDefaultBeanOfModule() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyEventListener.class);
            assertThat(context).hasSingleBean(MetricsIdempotencyEventListener.class);
            assertThat(context).hasSingleBean(CacheEventListener.class);
        });
    }

    @Test
    @DisplayName("IT context when no registry exists should fail to start")
    void context_whenNoRegistryExists_shouldFailToStart() {
        module()
                .withPropertyValues("idempify.metrics.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when the application brings a listener of its own should not register the micrometer one")
    void context_whenApplicationBringsListenerOfItsOwn_shouldNotRegisterMicrometerOne() {
        contextRunner
                .withBean(IdempotencyEventListener.class, () -> IdempotencyEventListener.NOOP)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(MetricsIdempotencyEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when metrics are on should register the counting cache listener")
    void context_whenMetricsAreOn_shouldRegisterCountingCacheListener() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CacheEventListener.class);
            assertThat(context.getBean(CacheEventListener.class)).isInstanceOf(MetricsCacheEventListener.class);
        });
    }

    @Test
    @DisplayName("IT context when the application brings a cache listener of its own should not register the metrics one")
    void context_whenApplicationBringsCacheListenerOfItsOwn_shouldNotRegisterMetricsOne() {
        // given
        CacheEventListener own = CacheEventListener.NOOP;

        // when / then
        contextRunner
                .withBean(CacheEventListener.class, () -> own)
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheEventListener.class);
                    assertThat(context.getBean(CacheEventListener.class)).isSameAs(own);
                    assertThat(context).doesNotHaveBean(MetricsCacheEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when the registered listener is told about a miss should count it")
    void context_whenRegisteredListenerIsToldAboutMiss_shouldCountIt() {
        contextRunner.run(context -> {
            CacheEventListener listener = context.getBean(CacheEventListener.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            listener.onMiss();

            assertThat(registry.get("idempify.cache.gets").tag("result", "miss").counter().count()).isEqualTo(1.0);
            assertThat(registry.get("idempify.cache.gets").tag("result", "hit").counter().count()).isZero();
        });
    }

    @Test
    @DisplayName("IT context when metrics are switched off should register nothing at all")
    void context_whenMetricsAreSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(CacheEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when nothing names the property should register nothing at all")
    void context_whenNothingNamesProperty_shouldRegisterNothingAtAll() {
        module()
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(CacheEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(CacheEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when micrometer is absent should register nothing at all")
    void context_whenMicrometerIsAbsent_shouldRegisterNothingAtAll() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(CacheEventListener.class);
                });
    }

    @Test
    @DisplayName("IT context when core is loaded too and metrics are asked for should let micrometer answer the hook")
    void context_whenCoreIsLoadedTooAndMetricsAreAskedFor_shouldLetMicrometerAnswerHook() {
        withCore().withPropertyValues("idempify.metrics.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(IdempotencyEventListener.class);
            assertThat(context).hasSingleBean(MetricsIdempotencyEventListener.class);
            assertThat(context.getBean(IdempotencyEventListener.class))
                    .isNotSameAs(IdempotencyEventListener.NOOP);
        });
    }

    @Test
    @DisplayName("IT context when core is loaded too and metrics are switched off should let the no-op answer the hook")
    void context_whenCoreIsLoadedTooAndMetricsAreSwitchedOff_shouldLetNoOpAnswerHook() {
        withCore()
                .withPropertyValues("idempify.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context).doesNotHaveBean(MetricsIdempotencyEventListener.class);
                    assertThat(context.getBean(IdempotencyEventListener.class))
                            .isSameAs(IdempotencyEventListener.NOOP);
                });
    }

    @Test
    @DisplayName("IT context when core is loaded too and nothing names the property should let the no-op answer the hook")
    void context_whenCoreIsLoadedTooAndNothingNamesProperty_shouldLetNoOpAnswerHook() {
        withCore().run(context -> {
            assertThat(context).hasSingleBean(IdempotencyEventListener.class);
            assertThat(context).doesNotHaveBean(MetricsIdempotencyEventListener.class);
            assertThat(context.getBean(IdempotencyEventListener.class)).isSameAs(IdempotencyEventListener.NOOP);
        });
    }

    @Test
    @DisplayName("IT context when core is loaded too and micrometer is absent should fail naming micrometer")
    void context_whenCoreIsLoadedTooAndMicrometerIsAbsent_shouldFailNamingMicrometer() {
        coreDependencies()
                .withConfiguration(AutoConfigurations.of(
                        IdempifyMetricsAutoConfiguration.class,
                        IdempifyCoreAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .withPropertyValues("idempify.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("idempify.metrics.enabled")
                            .hasStackTraceContaining("io.micrometer.core.instrument.MeterRegistry");
                });
    }

    /**
     * Loads core alongside this module, because the question these three tests ask - which of the two listeners
     * an application ends up with - is decided by auto-configuration order, and order does not exist inside a
     * context holding only one of them. Everything core needs from other modules is a stand-in here.
     */
    private static ApplicationContextRunner withCore() {
        return coreDependencies()
                .withConfiguration(AutoConfigurations.of(
                        IdempifyMetricsAutoConfiguration.class,
                        IdempifyCoreAutoConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);
    }

    private static ApplicationContextRunner module() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyMetricsAutoConfiguration.class));
    }

    static ApplicationContextRunner coreDependencies() {
        return new ApplicationContextRunner()
                .withBean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME, IdempotencyConfig.class,
                        IdempifyMetricsAutoConfigurationIntegrationTest::globalConfig)
                .withBean(TransactionalOperationRepository.class, () -> mock(TransactionalOperationRepository.class))
                .withBean(OperationRepository.class, () -> mock(OperationRepository.class))
                .withBean(ResponseRepository.class, () -> mock(ResponseRepository.class))
                .withBean(ResponseSerializer.class, () -> mock(ResponseSerializer.class))
                .withBean(ResponseDeserializer.class, () -> mock(ResponseDeserializer.class))
                .withBean(ResultSerializer.class, () -> mock(ResultSerializer.class))
                .withBean(ResultDeserializer.class, () -> mock(ResultDeserializer.class))
                .withBean(OperationStateChannel.class, () -> mock(OperationStateChannel.class))
                .withBean(KeyExtractor.class, StubKeyExtractor::new)
                .withBean(BodyCanonicalizerCreator.class, StubCanonicalizerCreator::new)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class));
    }

    private static IdempotencyConfig globalConfig() {
        return IdempotencyConfig.builder()
                .headerName(IdempifyDefaults.HEADER_NAME)
                .ttl(Duration.parse(IdempifyDefaults.TTL_VALUE))
                .processorType(ProcessorType.valueOf(IdempifyDefaults.PROCESSOR_TYPE_NAME))
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.defaults())
                .response(ResponseConfig.disabled())
                .build();
    }

    private static final class StubKeyExtractor implements KeyExtractor {

        @Override
        public UUID extract(String headerName, RequestContext context) {
            return null;
        }

        @Override
        public RequestType getRequestType() {
            return RequestType.HTTP;
        }
    }

    private static final class StubCanonicalizerCreator implements BodyCanonicalizerCreator {

        @Override
        public BodyCanonicalizer create(BodyCanonicalizerConfig config) {
            return null;
        }

        @Override
        public BodyFormat getFormat() {
            return BodyFormat.JSON;
        }
    }

}
