package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicyProvider;

import java.time.Duration;
import java.util.Objects;

public class DefaultOperationMetadataManager implements OperationMetadataManager {

    private final IdempotencyConfig defaultConfig;
    private final IdempotencyConfigRegistry configRegistry;
    private final ConflictHandlerProvider conflictHandlerProvider;
    private final FingerprintPolicyProvider fingerprintPolicyProvider;

    /**
     * @throws IllegalArgumentException if {@code defaultConfig} leaves a setting out - being the layer every
     * other one falls back on, it owes an answer for all of them, and a section missing here would surface
     * far from its cause, on the first call the resolver serves.
     */
    public DefaultOperationMetadataManager(IdempotencyConfig defaultConfig,
                                           IdempotencyConfigRegistry configRegistry,
                                           ConflictHandlerProvider conflictHandlerProvider,
                                           FingerprintPolicyProvider fingerprintPolicyProvider) {
        Objects.requireNonNull(defaultConfig, "defaultConfig cannot be null");
        if (!defaultConfig.notEmpty()) {
            throw new IllegalArgumentException(
                    "defaultConfig must specify every setting a merge can fall back on, but was %s".formatted(defaultConfig)
            );
        }
        this.defaultConfig = defaultConfig;
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry cannot be null");
        this.conflictHandlerProvider = Objects.requireNonNull(conflictHandlerProvider, "conflictHandlerProvider cannot be null");
        this.fingerprintPolicyProvider = Objects.requireNonNull(fingerprintPolicyProvider, "fingerprintPolicyProvider cannot be null");
    }

    @Override
    public OperationMetadata merge(RawOperationMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        return buildMetadata(merge(defaultConfig, metadata));
    }

    @Override
    public OperationMetadata merge(RawOperationMetadata metadata, String configName) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        if (StringUtils.isBlank(configName)) {
            throw new IllegalArgumentException("configName cannot be null, empty or blank");
        }
        IdempotencyConfig mergedConfig = IdempotencyConfig.merge(defaultConfig, getConfig(configName));
        return buildMetadata(merge(mergedConfig, metadata));
    }

    private IdempotencyConfig getConfig(String name) {
        IdempotencyConfig config = configRegistry.get(name);
        if (config == null) {
            throw new IllegalStateException("IdempotentEndpointConfig with %s name not found".formatted(name));
        }
        return config;
    }

    public IdempotencyConfig merge(IdempotencyConfig config, RawOperationMetadata rawMetadata) {
        IdempotencyConfig.Builder configBuilder = IdempotencyConfig.builder(config);

        mergeHeaderName(configBuilder, rawMetadata);
        mergeTtl(configBuilder, rawMetadata);

        ProcessorType processorType = ProcessorTypeToggle.toProcessorType(rawMetadata.getProcessorType());
        if (processorType != null) {
            configBuilder.processorType(processorType);
        }

        if (ProcessorType.TRANSACTIONAL.equals(processorType)) {
            configBuilder.conflict(ConflictConfig.disabled());
            configBuilder.responseCache(ResponseCacheConfig.disabled());
        } else {
            mergeConflict(config, configBuilder, rawMetadata);
            mergeResponseCache(config, configBuilder, rawMetadata);
        }

        mergeFingerprint(config, configBuilder, rawMetadata);

        IdempotencyConfig mergedConfig = configBuilder.build();
        mergedConfig.validate();

        return mergedConfig;
    }

    private void mergeHeaderName(IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        String headerName = rawMetadata.getHeaderName();
        if (!StringUtils.isBlank(headerName)) {
            builder.headerName(headerName);
        }
    }

    private void mergeTtl(IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        Duration ttl = rawMetadata.getTtl();
        if (ttl != null && !ttl.isNegative()) {
            builder.ttl(ttl);
        }
    }

    private void mergeConflict(IdempotencyConfig config, IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        ConflictHandleStrategy conflictHandleStrategy =
                ConflictHandleStrategyToggle.toConflictHandleStrategy(rawMetadata.getConflictHandleStrategy());
        ConflictConfig conflictConfig = config.getConflictConfig();
        if (conflictHandleStrategy != null) {
            ConflictConfig.Builder conflictConfigBuilder = ConflictConfig.builder(conflictConfig);
            conflictConfigBuilder.enabled(true);
            conflictConfigBuilder.strategy(conflictHandleStrategy);

            ConflictConfig defaultConflictConfig = defaultConfig.getConflictConfig();
            if (defaultConflictConfig.isEnabled()
                    && defaultConflictConfig.getStrategy().equals(conflictHandleStrategy)) {
                conflictConfigBuilder.handlerConfig(defaultConflictConfig.getHandlerConfig());
            } else {
                conflictConfigBuilder.handlerConfig(ConflictConfig.getDefaultHandlerConfig(conflictHandleStrategy));
            }

            builder.conflict(conflictConfigBuilder.build());
        }
    }

    private void mergeFingerprint(IdempotencyConfig config, IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        Boolean useFingerprint = Toggle.toBoolean(rawMetadata.getFingerprintToggle());
        FingerprintConfig fingerprintConfig = config.getFingerprintConfig();
        if (useFingerprint != null) {
            if (useFingerprint && (fingerprintConfig == null || !fingerprintConfig.isEnabled())) {
                FingerprintConfig defaultFingerprintConfig = defaultConfig.getFingerprintConfig();
                if (defaultFingerprintConfig.isEnabled()) {
                    builder.fingerprint(defaultFingerprintConfig);
                } else {
                    builder.fingerprint(FingerprintConfig.defaults());
                }
            } else if (!useFingerprint) {
                builder.fingerprint(FingerprintConfig.disabled());
            }
        }
    }

    private void mergeResponseCache(IdempotencyConfig config, IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        if (!config.getResponseCacheConfig().isEnabled()) {
            builder.responseCache(ResponseCacheConfig.disabled());
        }

        Boolean useCache = Toggle.toBoolean(rawMetadata.getCacheToggle());
        if (useCache != null) {
            if (useCache) {
                ResponseCacheConfig.Builder cacheConfigBuilder = ResponseCacheConfig.builder(config.getResponseCacheConfig());

                ResponseCacheConfig defaultResponseCacheConfig = defaultConfig.getResponseCacheConfig();

                Boolean cache4xx = Toggle.toBoolean(rawMetadata.getCache4xxToggle());
                if (cache4xx != null) {
                    cacheConfigBuilder.shouldCache4xx(cache4xx);
                } else {
                    if (defaultResponseCacheConfig.isEnabled()) {
                        cacheConfigBuilder.shouldCache4xx(defaultResponseCacheConfig.shouldCache4xx());
                    }
                }

                Boolean cache5xx = Toggle.toBoolean(rawMetadata.getCache5xxToggle());
                if (cache5xx != null) {
                    cacheConfigBuilder.shouldCache5xx(cache5xx);
                } else {
                    if (defaultResponseCacheConfig.isEnabled()) {
                        cacheConfigBuilder.shouldCache5xx(defaultResponseCacheConfig.shouldCache5xx());
                    }
                }

                builder.responseCache(cacheConfigBuilder.build());
            } else {
                builder.responseCache(ResponseCacheConfig.disabled());
            }
        }
    }

    private OperationMetadata buildMetadata(IdempotencyConfig config) {
        return DefaultOperationMetadata.builder()
                .headerName(config.getHeaderName())
                .ttl(config.getTtl())
                .processorType(config.getProcessorType())
                .conflictHandler(conflictHandlerProvider.provide(config.getConflictConfig()))
                .fingerprintPolicy(fingerprintPolicyProvider.provide(config.getFingerprintConfig()))
                .responseCacheConfig(config.getResponseCacheConfig())
                .build();
    }
}
