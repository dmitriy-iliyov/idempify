package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.cache.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.DelegatingKeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.response.*;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
public class IdempifyCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotentEntryPresenceCheck idempifyIdempotentEntryPresenceCheck(
            ObjectProvider<IdempotentEntry> entries
    ) {
        return new IdempotentEntryPresenceCheck(entries.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyConfigRegistry idempifyIdempotencyConfigRegistry() {
        return new DefaultIdempotencyConfigRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConflictHandlerProvider idempifyConflictHandlerProvider(
            NonBlockingOperationRepository repository,
            OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> resultDeserializer,
            Clock clock
    ) {
        return new DefaultConflictHandlerProvider(
                repository,
                resultDeserializer,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public BodyCanonicalizerProvider idempifyBodyCanonicalizerProvider(List<BodyCanonicalizerCreator> creators) {
        return new DefaultBodyCanonicalizerProvider(creators);
    }

    @Bean
    @ConditionalOnMissingBean
    public FingerprintPolicyProvider idempifyFingerprintPolicyProvider(BodyCanonicalizerProvider bodyCanonicalizerProvider) {
        return new DefaultFingerprintPolicyProvider(bodyCanonicalizerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FingerprintMatcher idempifyFingerprintMatcher(IdempotencyEventListener eventListener) {
        return new DefaultFingerprintMatcher(eventListener);
    }

    @Primary
    @Bean
    public KeyExtractor idempifyDelegatingKeyExtractor(List<KeyExtractor> extractors) {
        return new DelegatingKeyExtractor(extractors);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMetricsDisabled
    public IdempotencyEventListener idempifyIdempotencyEventListener() {
        return IdempotencyEventListener.NOOP;
    }

    @Bean
    @ConditionalOnCacheEnabled
    @ConditionalOnMissingBean
    @ConditionalOnMetricsDisabled
    public CacheEventListener idempifyCacheEventListener() {
        return CacheEventListener.NOOP;
    }

    @Bean
    @ConditionalOnCacheEnabled
    @ConditionalOnCacheType(type = CacheType.IN_MEMORY)
    public ResponseRepositoryWrapper idempifyInMemoryCacheResponseRepositoryWrapper(
            CachePropertiesHolder cachePropertiesHolder,
            Clock clock,
            CacheEventListener listener
    ) {
        return new ResponseRepositoryWrapper() {
            @Override
            public ResponseRepository wrap(ResponseRepository repository) {
                return new InMemoryCacheResponseRepositoryDecorator(
                        repository,
                        cachePropertiesHolder.getCacheCapacity(),
                        clock,
                        listener
                );
            }

            @Override
            public int getPriority() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationSerializer<Operation, RawOperation> idempifyOperationSerializer(
            ResultSerializer resultSerializer,
            ResponseSerializer responseSerializer
    ) {
        return new DefaultOperationSerializer(resultSerializer, responseSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationDeserializer<RawOperation, Operation> idempifyOperationDeserializer(
            ResultDeserializer resultDeserializer,
            ResponseDeserializer responseDeserializer
    ) {
        return new DefaultOperationDeserializer(resultDeserializer, responseDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataManager idempifyOperationMetadataManager(
            @Qualifier(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME) IdempotencyConfig config,
            IdempotencyConfigRegistry configRegistry,
            ConflictHandlerProvider conflictHandlerProvider,
            FingerprintPolicyProvider fingerprintPolicyProvider
    ) {
        return new DefaultOperationMetadataManager(
                config,
                configRegistry,
                conflictHandlerProvider,
                fingerprintPolicyProvider
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataCache idempifyOperationMetadataCache() {
        return new DefaultOperationMetadataCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataResolver idempifyOperationMetadataResolver(OperationMetadataCache cache,
                                                                       OperationMetadataManager manager) {
        return new DefaultOperationMetadataResolver(cache, manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseManager idempifyResponseManager(ResponseRepository responseRepository,
                                                   ResponseSerializer responseSerializer,
                                                   ResponseDeserializer responseDeserializer) {
        return new DefaultResponseManager(responseRepository, responseSerializer, responseDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public BlockingOperationManager idempifyBlockingOperationManager(
            BlockingOperationRepository repository,
            FingerprintMatcher fingerprintMatcher,
            OperationSerializer<Operation, RawOperation> serializer,
            OperationDeserializer<RawOperation, Operation> deserializer,
            ResultSerializer resultSerializer
    ) {
        return new DefaultBlockingOperationManager(
                repository,
                fingerprintMatcher,
                serializer,
                deserializer,
                resultSerializer
        );
    }

    @Bean
    @ConditionalOnMissingProcessorType(type = ProcessorType.BLOCKING)
    @ProvidesProcessorType(type = ProcessorType.BLOCKING)
    public TypeAwareIdempotentProcessor idempifyBlockingTypeAwareIdempotentProcessor(
            TransactionTemplate transactionTemplate,
            BlockingOperationManager manager,
            OperationStateChannel channel,
            IdempotencyEventListener eventListener
    ) {
        return new BlockingIdempotentProcessor(transactionTemplate, manager, channel, eventListener);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> idempifyNonBlockingOperationSerializer(
            ResultSerializer resultSerializer,
            ResponseSerializer responseSerializer
    ) {
        return new NonBlockingOperationSerializer(resultSerializer, responseSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> idempifyNonBlockingOperationDeserializer(
            ResultDeserializer resultDeserializer,
            ResponseDeserializer responseDeserializer
    ) {
        return new NonBlockingOperationDeserializer(resultDeserializer, responseDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public LockTokenGenerator idempifyLockTokenGenerator() {
        return new DefaultLockTokenGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public NonBlockingOperationManager idempifyNonBlockingOperationManager(
            NonBlockingOperationRepository repository,
            LockTokenGenerator lockTokenGenerator,
            OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> serializer,
            OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserializer,
            FingerprintMatcher fingerprintMatcher,
            ResultSerializer resultSerializer,
            IdempotencyEventListener eventListener
    ) {
        return new DefaultNonBlockingOperationManager(
                repository,
                lockTokenGenerator,
                serializer,
                deserializer,
                fingerprintMatcher,
                resultSerializer,
                eventListener
        );
    }

    @Bean
    @ConditionalOnMissingProcessorType(type = ProcessorType.NON_BLOCKING)
    @ProvidesProcessorType(type = ProcessorType.NON_BLOCKING)
    public TypeAwareIdempotentProcessor idempifyNonBlockingTypeAwareIdempotentProcessor(
            NonBlockingOperationManager manager,
            OperationStateChannel channel,
            IdempotencyEventListener eventListener
    ) {
        return new NonBlockingIdempotentProcessor(manager, channel, eventListener);
    }

    @Primary
    @Bean
    public IdempotentProcessor idempifyDelegatingIdempotentProcessor(List<TypeAwareIdempotentProcessor> processors) {
        return new DelegatingIdempotentProcessor(processors);
    }

    public static final class IdempotentEntryPresenceCheck {

        public IdempotentEntryPresenceCheck(List<IdempotentEntry> entries) {
            Objects.requireNonNull(entries, "entries cannot be null");
            if (entries.isEmpty()) {
                throw new IllegalStateException("""
                        No IdempotentEntry is registered, so nothing turns an annotated call into an operation: \
                        add idempify-aop, register an entry of your own, or switch the library off with \
                        idempify.enabled=false""");
            }
        }
    }
}
