package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.cache.*;
import io.github.dmitriyiliyov.idempify.core.config.DefaultIdempotencyConfigRegistry;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfigRegistry;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.DelegatingKeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.response.*;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
public class IdempifyCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyConfigRegistry idempifyIdempotencyConfigRegistry() {
        return new DefaultIdempotencyConfigRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConflictHandlerProvider idempifyConflictHandlerProvider(OperationRepository repository,
                                                                   ResultDeserializer resultDeserializer,
                                                                   Clock clock) {
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
    @ConditionalOnProperty(
            prefix = "idempify.metrics",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    @ConditionalOnMissingBean
    public IdempotencyEventListener idempifyIdempotencyEventListener() {
        return IdempotencyEventListener.NOOP;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "idempify.metrics",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    @ConditionalOnMissingBean
    public CacheEventListener idempifyCacheEventListener() {
        return CacheEventListener.NOOP;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "idempify.cache",
            name = "enabled",
            havingValue = "true"
    )
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
    public OperationSerializer idempifyOperationSerializer(ResultSerializer resultSerializer,
                                                           ResponseSerializer responseSerializer) {
        return new DefaultOperationSerializer(resultSerializer, responseSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationDeserializer idempifyOperationDeserializer(ResultDeserializer resultDeserializer,
                                                               ResponseDeserializer responseDeserializer) {
        return new DefaultOperationDeserializer(resultDeserializer, responseDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationCreator idempifyOperationCreator() {
        return new DefaultOperationCreator();
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
    public TransactionalOperationManager idempifyTransactionalOperationManager(
            OperationCreator mapper,
            TransactionalOperationRepository repository,
            FingerprintMatcher fingerprintMatcher,
            OperationSerializer serializer,
            OperationDeserializer deserializer,
            ResultSerializer resultSerializer,
            Clock clock
    ) {
        return new DefaultTransactionalOperationManager(
                mapper,
                repository,
                fingerprintMatcher,
                serializer,
                deserializer,
                resultSerializer,
                clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TypeAwareIdempotentProcessor idempifyTransactionalTypeAwareIdempotentProcessor(
            TransactionTemplate transactionTemplate,
            TransactionalOperationManager manager,
            OperationStateChannel channel,
            IdempotencyEventListener eventListener
    ) {
        return new TransactionalIdempotentProcessor(transactionTemplate, manager, channel, eventListener);
    }

    @Primary
    @Bean
    public IdempotentProcessor idempifyDelegatingIdempotentProcessor(List<TypeAwareIdempotentProcessor> processors) {
        return new DelegatingIdempotentProcessor(processors);
    }
}
