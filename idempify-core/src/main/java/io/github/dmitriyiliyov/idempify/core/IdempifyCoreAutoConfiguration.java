package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.DefaultIdempotencyConfigRegistry;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfigRegistry;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.DelegatingKeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.response.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Set;

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
                                                                   ResultDeserializer deserializer,
                                                                   Clock clock) {
        return new DefaultConflictHandlerProvider(repository, deserializer, clock);
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
            prefix = "idempify.cache",
            name = "enabled",
            havingValue = "true"
    )
    @ConditionalOnMissingBean
    public ResponseCache idempifyInMemoryResponseCache(CachePropertiesHolder cachePropertiesHolder,
                                                       Clock clock,
                                                       Set<ResponseCacheWrapper> wrappers) {
        return ResponseCacheWrapperUtils.wrapWithPriority(
                new InMemoryResponseCache(cachePropertiesHolder.getInMemoryCacheCapacity(), clock),
                wrappers
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMapper idempifyOperationMapper() {
        return new DefaultOperationMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationMetadataCache idempifyOperationMetadataCache() {
        return new DefaultOperationMetadataCache();
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
    public OperationMetadataResolver idempifyOperationMetadataResolver(OperationMetadataCache cache,
                                                                       OperationMetadataManager manager) {
        return new DefaultOperationMetadataResolver(cache, manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalOperationManager idempifyTransactionalOperationManager(
            OperationMapper mapper,
            TransactionalOperationRepository repository,
            FingerprintMatcher fingerprintMatcher,
            ResultSerializer resultSerializer,
            ResultDeserializer resultDeserializer,
            Clock clock
    ) {
        return new DefaultTransactionalOperationManager(
                mapper,
                repository,
                fingerprintMatcher,
                resultSerializer,
                resultDeserializer,
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
