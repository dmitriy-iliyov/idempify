package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.conflict.RejectConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.DelegatingKeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import io.github.dmitriyiliyov.idempify.core.response.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks what only a context can answer: which beans the module contributes, which of them a user bean
 * replaces, which foreign bean it cannot start without, and what the {@code idempify.*} switches decide.
 * <p>
 * Everything the module needs from other modules - the store, the (de)serializer, the state channel, the
 * transport's key extractor, a canonicalizer creator - is a stand-in here, since none of it is what these
 * tests judge.
 */
class IdempifyCoreAutoConfigurationIntegrationTest {

    private static final String DEFAULT_CONFIG_BEAN_NAME = IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME;
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final ApplicationContextRunner contextRunner = contextRunnerWithEverything();

    @Test
    @DisplayName("IT autoConfiguration should be registered so that an application only adds the dependency")
    void autoConfiguration_shouldBeRegisteredSoThatApplicationOnlyAddsDependency() {
        // when
        Iterable<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        // then
        assertThat(candidates).contains(IdempifyCoreAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("IT context when required beans exist should register every default bean of the module")
    void context_whenRequiredBeansExist_shouldRegisterEveryDefaultBeanOfModule() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyConfigRegistry.class);
            assertThat(context).hasSingleBean(DefaultIdempotencyConfigRegistry.class);

            assertThat(context).hasSingleBean(ConflictHandlerProvider.class);
            assertThat(context).hasSingleBean(DefaultConflictHandlerProvider.class);

            assertThat(context).hasSingleBean(BodyCanonicalizerProvider.class);
            assertThat(context).hasSingleBean(DefaultBodyCanonicalizerProvider.class);

            assertThat(context).hasSingleBean(FingerprintPolicyProvider.class);
            assertThat(context).hasSingleBean(DefaultFingerprintPolicyProvider.class);

            assertThat(context).hasSingleBean(FingerprintMatcher.class);
            assertThat(context).hasSingleBean(DefaultFingerprintMatcher.class);

            assertThat(context).hasSingleBean(OperationMapper.class);
            assertThat(context).hasSingleBean(DefaultOperationMapper.class);

            assertThat(context).hasSingleBean(OperationMetadataCache.class);
            assertThat(context).hasSingleBean(DefaultOperationMetadataCache.class);

            assertThat(context).hasSingleBean(OperationMetadataManager.class);
            assertThat(context).hasSingleBean(DefaultOperationMetadataManager.class);

            assertThat(context).hasSingleBean(OperationMetadataResolver.class);
            assertThat(context).hasSingleBean(DefaultOperationMetadataResolver.class);

            assertThat(context).hasSingleBean(TransactionalOperationManager.class);
            assertThat(context).hasSingleBean(DefaultTransactionalOperationManager.class);

            assertThat(context).hasSingleBean(TypeAwareIdempotentProcessor.class);
            assertThat(context).hasSingleBean(TransactionalIdempotentProcessor.class);

            assertThat(context).hasSingleBean(DelegatingKeyExtractor.class);
            assertThat(context).hasSingleBean(DelegatingIdempotentProcessor.class);
        });
    }

    @Test
    @DisplayName("IT context when nothing asks for a response cache should register none")
    void context_whenNothingAsksForResponseCache_shouldRegisterNone() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ResponseCache.class);
        });
    }

    @Test
    @DisplayName("IT context when the module is assembled should answer both entry points with the same resolver")
    void context_whenModuleIsAssembled_shouldAnswerBothEntryPointsWithSameResolver() {
        contextRunner.run(context -> {
            OperationMetadataResolver resolver = context.getBean(OperationMetadataResolver.class);

            OperationMetadata metadata = resolver.resolve(paymentMethod(), PaymentService.class);

            assertThat(metadata.getHeaderName()).isEqualTo(IdempifyDefaults.HEADER_NAME);
            assertThat(metadata.getTtl()).isEqualTo(Duration.parse(IdempifyDefaults.TTL_VALUE));
            assertThat(metadata.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);
            assertThat(metadata.getConflictHandler()).isInstanceOf(RejectConflictHandler.class);
            assertThat(metadata.useFingerprint()).isTrue();
        });
    }

    @Test
    @DisplayName("IT context when the call site fingerprints should reach the canonicalizer the creators contributed")
    void context_whenCallSiteFingerprints_shouldReachCanonicalizerCreatorsContributed() {
        contextRunner.run(context -> {
            OperationMetadata metadata = context.getBean(OperationMetadataResolver.class)
                    .resolve(paymentMethod(), PaymentService.class);

            String fingerprint = metadata.getFingerprintPolicy().generate(request());

            assertThat(fingerprint).isNotBlank();
        });
    }

    @Test
    @DisplayName("IT context when another IdempotencyConfig bean exists should layer the named one under the call sites")
    void context_whenAnotherIdempotencyConfigBeanExists_shouldLayerNamedOneUnderCallSites() {
        contextRunner
                .withBean("paymentsIdempotencyConfig", IdempotencyConfig.class,
                        () -> IdempotencyConfig.builder().headerName("X-Payments-Key").build())
                .run(context -> {
                    OperationMetadata metadata = context.getBean(OperationMetadataResolver.class)
                            .resolve(paymentMethod(), PaymentService.class);

                    assertThat(metadata.getHeaderName()).isEqualTo(IdempifyDefaults.HEADER_NAME);
                });
    }

    @Test
    @DisplayName("IT context when no global config bean exists should fail to start")
    void context_whenNoGlobalConfigBeanExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.GLOBAL_CONFIG)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no OperationRepository exists should fail to start")
    void context_whenNoOperationRepositoryExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.OPERATION_REPOSITORY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no ResultSerializer exists should fail to start")
    void context_whenNoResultSerializerExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.RESULT_SERIALIZER)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no ResultDeserializer exists should fail to start")
    void context_whenNoResultDeserializerExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.RESULT_DESERIALIZER)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no OperationStateChannel exists should fail to start")
    void context_whenNoOperationStateChannelExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.STATE_CHANNEL)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no TransactionTemplate exists should fail to start")
    void context_whenNoTransactionTemplateExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.TRANSACTION_TEMPLATE)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no Clock exists should fail to start")
    void context_whenNoClockExists_shouldFailToStart() {
        contextRunnerWithout(Dependency.CLOCK)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no module contributes a BodyCanonicalizerCreator should say so and fail")
    void context_whenNoModuleContributesBodyCanonicalizerCreator_shouldSaySoAndFail() {
        contextRunnerWithout(Dependency.BODY_CANONICALIZER_CREATOR)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("At least one BodyCanonicalizerCreator must be registered");
                });
    }

    @Test
    @DisplayName("IT context when no transport contributes a KeyExtractor should say so and fail")
    void context_whenNoTransportContributesKeyExtractor_shouldSaySoAndFail() {
        contextRunnerWithout(Dependency.KEY_EXTRACTOR)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("At least one KeyExtractor must be registered");
                });
    }

    @Test
    @DisplayName("IT context when two transports claim the same request type should name both and fail")
    void context_whenTwoTransportsClaimSameRequestType_shouldNameBothAndFail() {
        contextRunner
                .withBean("anotherHttpKeyExtractor", KeyExtractor.class, TestKeyExtractor::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Exactly one KeyExtractor must be registered for request type HTTP");
                });
    }

    @Test
    @DisplayName("IT context when existing IdempotencyConfigRegistry bean should not register the default one")
    void context_whenExistingIdempotencyConfigRegistryBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(IdempotencyConfigRegistry.class, () -> mock(IdempotencyConfigRegistry.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyConfigRegistry.class);
                    assertThat(context).doesNotHaveBean(DefaultIdempotencyConfigRegistry.class);
                });
    }

    @Test
    @DisplayName("IT context when existing ConflictHandlerProvider bean should not register the default one")
    void context_whenExistingConflictHandlerProviderBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(ConflictHandlerProvider.class, () -> mock(ConflictHandlerProvider.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ConflictHandlerProvider.class);
                    assertThat(context).doesNotHaveBean(DefaultConflictHandlerProvider.class);
                });
    }

    @Test
    @DisplayName("IT context when existing BodyCanonicalizerProvider bean should not register the default one")
    void context_whenExistingBodyCanonicalizerProviderBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(BodyCanonicalizerProvider.class, () -> mock(BodyCanonicalizerProvider.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(BodyCanonicalizerProvider.class);
                    assertThat(context).doesNotHaveBean(DefaultBodyCanonicalizerProvider.class);
                });
    }

    @Test
    @DisplayName("IT context when existing FingerprintPolicyProvider bean should not register the default one")
    void context_whenExistingFingerprintPolicyProviderBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(FingerprintPolicyProvider.class, () -> mock(FingerprintPolicyProvider.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(FingerprintPolicyProvider.class);
                    assertThat(context).doesNotHaveBean(DefaultFingerprintPolicyProvider.class);
                });
    }

    @Test
    @DisplayName("IT context when existing FingerprintMatcher bean should not register the default one")
    void context_whenExistingFingerprintMatcherBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(FingerprintMatcher.class, () -> mock(FingerprintMatcher.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(FingerprintMatcher.class);
                    assertThat(context).doesNotHaveBean(DefaultFingerprintMatcher.class);
                });
    }

    @Test
    @DisplayName("IT context when existing OperationMapper bean should not register the default one")
    void context_whenExistingOperationMapperBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(OperationMapper.class, () -> mock(OperationMapper.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMapper.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMapper.class);
                });
    }

    @Test
    @DisplayName("IT context when existing OperationMetadataCache bean should not register the default one")
    void context_whenExistingOperationMetadataCacheBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(OperationMetadataCache.class, () -> mock(OperationMetadataCache.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataCache.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMetadataCache.class);
                });
    }

    @Test
    @DisplayName("IT context when existing OperationMetadataManager bean should not register the default one")
    void context_whenExistingOperationMetadataManagerBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(OperationMetadataManager.class, () -> mock(OperationMetadataManager.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataManager.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMetadataManager.class);
                });
    }

    @Test
    @DisplayName("IT context when existing OperationMetadataResolver bean should not register the default one")
    void context_whenExistingOperationMetadataResolverBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(OperationMetadataResolver.class, () -> mock(OperationMetadataResolver.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataResolver.class);
                    assertThat(context).doesNotHaveBean(DefaultOperationMetadataResolver.class);
                });
    }

    @Test
    @DisplayName("IT context when existing TransactionalOperationManager bean should not register the default one")
    void context_whenExistingTransactionalOperationManagerBean_shouldNotRegisterDefaultOne() {
        contextRunner
                .withBean(TransactionalOperationManager.class, () -> mock(TransactionalOperationManager.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TransactionalOperationManager.class);
                    assertThat(context).doesNotHaveBean(DefaultTransactionalOperationManager.class);
                });
    }

    @Test
    @DisplayName("IT context when the application serves a processor type itself should not register the transactional one")
    void context_whenApplicationServesProcessorTypeItself_shouldNotRegisterTransactionalOne() {
        contextRunner
                .withBean(TypeAwareIdempotentProcessor.class, LockBasedTestProcessor::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(TypeAwareIdempotentProcessor.class);
                    assertThat(context).doesNotHaveBean(TransactionalIdempotentProcessor.class);
                });
    }

    @Test
    @DisplayName("IT context when a transport asks for the key extractor should hand over the delegating one")
    void context_whenTransportAsksForKeyExtractor_shouldHandOverDelegatingOne() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(KeyExtractor.class)).hasSize(2);
            assertThat(context.getBean(KeyExtractor.class)).isInstanceOf(DelegatingKeyExtractor.class);
        });
    }

    @Test
    @DisplayName("IT context when a transport contributes a key extractor should route its own requests to it")
    void context_whenTransportContributesKeyExtractor_shouldRouteItsOwnRequestsToIt() {
        contextRunner.run(context -> {
            KeyExtractor keyExtractor = context.getBean(KeyExtractor.class);

            UUID extracted = keyExtractor.extract(IdempifyDefaults.HEADER_NAME, request());

            assertThat(extracted).isEqualTo(KEY);
        });
    }

    @Test
    @DisplayName("IT context when an entry point asks for the processor should hand over the delegating one")
    void context_whenEntryPointAsksForProcessor_shouldHandOverDelegatingOne() {
        contextRunner.run(context ->
                assertThat(context.getBean(IdempotentProcessor.class)).isInstanceOf(DelegatingIdempotentProcessor.class));
    }

    @Test
    @DisplayName("IT context when the application serves a processor type itself should route that type to it")
    void context_whenApplicationServesProcessorTypeItself_shouldRouteThatTypeToIt() {
        contextRunner
                .withBean(TypeAwareIdempotentProcessor.class, LockBasedTestProcessor::new)
                .run(context -> {
                    IdempotentProcessor processor = context.getBean(IdempotentProcessor.class);

                    String result = processor.process(
                            new DefaultOperationContext<>(String.class, () -> "charged", KEY, null),
                            TestOperationMetadata.builder().processorType(ProcessorType.LOCK_BASED).build()
                    );

                    assertThat(result).isEqualTo("lock-based");
                });
    }

    @Test
    @DisplayName("IT context when caching is switched on should register the in-memory cache")
    void context_whenCachingIsSwitchedOn_shouldRegisterInMemoryCache() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseCache.class);
                    assertThat(context).hasSingleBean(InMemoryResponseCache.class);
                });
    }

    @Test
    @DisplayName("IT context when caching is switched on should bound the cache by the capacity the properties hold")
    void context_whenCachingIsSwitchedOn_shouldBoundCacheByCapacityPropertiesHold() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context -> {
                    ResponseCache cache = context.getBean(ResponseCache.class);

                    cache.save(KEY, response(), Duration.ofMinutes(1));
                    cache.save(OTHER_KEY, response(), Duration.ofMinutes(1));

                    assertThat(cache.findByIdempotencyKey(KEY)).isNull();
                    assertThat(cache.findByIdempotencyKey(OTHER_KEY)).isNotNull();
                });
    }

    @Test
    @DisplayName("IT context when caching is switched on and a wrapper is registered should hand out the wrapped cache")
    void context_whenCachingIsSwitchedOnAndWrapperIsRegistered_shouldHandOutWrappedCache() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .withBean(ResponseCacheWrapper.class, CountingWrapper::new)
                .run(context -> {
                    ResponseCache cache = context.getBean(ResponseCache.class);

                    cache.save(KEY, response(), Duration.ofMinutes(1));
                    cache.findByIdempotencyKey(KEY);

                    assertThat(cache).isInstanceOf(CountingCache.class);
                    assertThat(((CountingCache) cache).lookups).isEqualTo(1);
                    assertThat(((CountingCache) cache).next).isInstanceOf(InMemoryResponseCache.class);
                });
    }

    @Test
    @DisplayName("IT context when nobody wraps the cache should hand out the in-memory one unwrapped")
    void context_whenNobodyWrapsCache_shouldHandOutInMemoryOneUnwrapped() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context -> assertThat(context.getBean(ResponseCache.class))
                        .isInstanceOf(InMemoryResponseCache.class));
    }

    @Test
    @DisplayName("IT context when caching is switched off should register no response cache")
    void context_whenCachingIsSwitchedOff_shouldRegisterNoResponseCache() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ResponseCache.class);
                });
    }

    @Test
    @DisplayName("IT context when caching is switched on with a backend of its own should not register the in-memory one")
    void context_whenCachingIsSwitchedOnWithBackendOfItsOwn_shouldNotRegisterInMemoryOne() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .withBean(ResponseCache.class, () -> mock(ResponseCache.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseCache.class);
                    assertThat(context).doesNotHaveBean(InMemoryResponseCache.class);
                });
    }

    @Test
    @DisplayName("IT context when caching is switched on but nobody holds the cache properties should fail to start")
    void context_whenCachingIsSwitchedOnButNobodyHoldsCacheProperties_shouldFailToStart() {
        contextRunnerWithout(Dependency.CACHE_PROPERTIES)
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when nothing else answers the hook should register the no-op listener")
    void context_whenNothingElseAnswersHook_shouldRegisterNoOpListener() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context.getBean(IdempotencyEventListener.class))
                            .isSameAs(IdempotencyEventListener.NOOP);
                });
    }

    @Test
    @DisplayName("IT context when metrics are switched off should answer the hook with the no-op listener")
    void context_whenMetricsAreSwitchedOff_shouldAnswerHookWithNoOpListener() {
        contextRunner
                .withPropertyValues("idempify.metrics.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context.getBean(IdempotencyEventListener.class))
                            .isSameAs(IdempotencyEventListener.NOOP);
                });
    }

    @Test
    @DisplayName("IT context when metrics are switched off and the application brings a listener should keep that one")
    void context_whenMetricsAreSwitchedOffAndApplicationBringsListener_shouldKeepThatOne() {
        // given
        IdempotencyEventListener own = new RecordingEventListener();

        // when / then
        contextRunner
                .withPropertyValues("idempify.metrics.enabled=false")
                .withBean(IdempotencyEventListener.class, () -> own)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context.getBean(IdempotencyEventListener.class)).isSameAs(own);
                });
    }

    @Test
    @DisplayName("IT context when the application brings a listener of its own should not register the no-op one")
    void context_whenApplicationBringsListenerOfItsOwn_shouldNotRegisterNoOpOne() {
        // given
        IdempotencyEventListener own = new RecordingEventListener();

        // when / then
        contextRunner
                .withBean(IdempotencyEventListener.class, () -> own)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyEventListener.class);
                    assertThat(context.getBean(IdempotencyEventListener.class)).isSameAs(own);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyConfigRegistry.class);
                    assertThat(context).doesNotHaveBean(OperationMetadataResolver.class);
                    assertThat(context).doesNotHaveBean(IdempotentProcessor.class);
                    assertThat(context).doesNotHaveBean(DelegatingKeyExtractor.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationMetadataResolver.class);
                    assertThat(context).hasSingleBean(DelegatingIdempotentProcessor.class);
                });
    }

    private static ApplicationContextRunner contextRunnerWithEverything() {
        return contextRunnerWithout(null);
    }

    private static ApplicationContextRunner contextRunnerWithout(Dependency omitted) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyCoreAutoConfiguration.class));
        for (Dependency dependency : Dependency.values()) {
            if (dependency != omitted) {
                runner = dependency.registerOn(runner);
            }
        }
        return runner;
    }

    /**
     * Stands in for an application's own listener - a class rather than a mock, so that the assertion reads
     * "this very bean" instead of "some listener".
     */
    private static final class RecordingEventListener implements IdempotencyEventListener {

        @Override
        public void onDuplicate() { }

        @Override
        public void onConflict() { }

        @Override
        public void onFingerprintMismatch() { }

        @Override
        public void onException() { }

        @Override
        public void onSuccess() { }
    }

    /**
     * Keeps the cache it wraps within reach and counts the lookups it saw, so a test reads both the nesting
     * and the fact that the wrapper is really in the path.
     */
    private static final class CountingCache extends AbstractResponseCacheDecorator {

        private final ResponseCache next;
        private int lookups;

        private CountingCache(ResponseCache delegate) {
            super(delegate);
            this.next = delegate;
        }

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            lookups++;
            return super.findByIdempotencyKey(idempotencyKey);
        }
    }

    private static final class CountingWrapper implements ResponseCacheWrapper {

        @Override
        public ResponseCache wrap(ResponseCache responseCache) {
            return new CountingCache(responseCache);
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }

    /**
     * What core expects to find in the context but never declares itself. Naming them one by one is what lets
     * a test say "everything but this one" without restating the other ten.
     */
    private enum Dependency {

        GLOBAL_CONFIG(runner -> runner.withBean(
                DEFAULT_CONFIG_BEAN_NAME, IdempotencyConfig.class,
                IdempifyCoreAutoConfigurationIntegrationTest::globalConfig)),
        OPERATION_REPOSITORY(runner -> runner.withBean(
                TransactionalOperationRepository.class, () -> mock(TransactionalOperationRepository.class))),
        RESULT_SERIALIZER(runner -> runner.withBean(ResultSerializer.class, () -> mock(ResultSerializer.class))),
        RESULT_DESERIALIZER(runner -> runner.withBean(ResultDeserializer.class, () -> mock(ResultDeserializer.class))),
        STATE_CHANNEL(runner -> runner.withBean(OperationStateChannel.class, () -> mock(OperationStateChannel.class))),
        TRANSACTION_TEMPLATE(runner -> runner.withBean(
                TransactionTemplate.class, () -> new TransactionTemplate(mock(PlatformTransactionManager.class)))),
        CLOCK(runner -> runner.withBean(Clock.class, TestClock::standingStill)),
        BODY_CANONICALIZER_CREATOR(runner -> runner.withBean(BodyCanonicalizerCreator.class, TestJsonCanonicalizerCreator::new)),
        KEY_EXTRACTOR(runner -> runner.withBean(KeyExtractor.class, TestKeyExtractor::new)),
        CACHE_PROPERTIES(runner -> runner.withBean(CachePropertiesHolder.class, TestCachePropertiesHolder::new));

        private final UnaryOperator<ApplicationContextRunner> registration;

        Dependency(UnaryOperator<ApplicationContextRunner> registration) {
            this.registration = registration;
        }

        private ApplicationContextRunner registerOn(ApplicationContextRunner runner) {
            return registration.apply(runner);
        }
    }

    /**
     * The global layer an application would hand over: complete, as {@link OperationMetadataManager} demands,
     * and fingerprinting the way the library does by default - through a canonicalizer, so that the creators
     * the context collected are on the path.
     */
    private static IdempotencyConfig globalConfig() {
        return IdempotencyConfig.builder()
                .headerName(IdempifyDefaults.HEADER_NAME)
                .ttl(Duration.parse(IdempifyDefaults.TTL_VALUE))
                .processorType(ProcessorType.valueOf(IdempifyDefaults.PROCESSOR_TYPE_NAME))
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.disabled())
                .build();
    }

    private static Method paymentMethod() throws NoSuchMethodException {
        return PaymentService.class.getMethod("pay");
    }

    private static RequestContext request() {
        return TestRequestContext.of("/payments", "POST", "{\"amount\":10}");
    }

    private static CachedResponse response() {
        return new DefaultCachedResponse(200, "{}".getBytes(StandardCharsets.UTF_8), "application/json", null);
    }

    static class PaymentService {

        @Idempotent
        public void pay() { }
    }

    /**
     * Stands in for the transport module, which is where the only {@link KeyExtractor} implementations live.
     */
    private static final class TestKeyExtractor implements KeyExtractor {

        @Override
        public UUID extract(String headerName, RequestContext context) {
            return KEY;
        }

        @Override
        public RequestType getRequestType() {
            return RequestType.HTTP;
        }
    }

    /**
     * Stands in for idempify-jackson: core declares the creator contract but reads no format itself.
     */
    private static final class TestJsonCanonicalizerCreator implements BodyCanonicalizerCreator {

        @Override
        public BodyCanonicalizer create(BodyCanonicalizerConfig config) {
            return bytes -> new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public BodyFormat getFormat() {
            return BodyFormat.JSON;
        }
    }

    private static final class TestCachePropertiesHolder implements CachePropertiesHolder {

        @Override
        public String getCacheName() {
            return "idempify";
        }

        @Override
        public int getInMemoryCacheCapacity() {
            return 1;
        }
    }

    private static final class LockBasedTestProcessor implements TypeAwareIdempotentProcessor {

        @Override
        public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
            return context.getOperationResultType().cast("lock-based");
        }

        @Override
        public ProcessorType getType() {
            return ProcessorType.LOCK_BASED;
        }
    }
}
