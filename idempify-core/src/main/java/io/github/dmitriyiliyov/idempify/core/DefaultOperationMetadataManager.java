package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerProvider;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class DefaultOperationMetadataManager implements OperationMetadataManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperationMetadataManager.class);
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

    /**
     * Layers the call site over the config already resolved for it, and refuses a result that contradicts
     * itself.
     * <p>
     * A call site that goes {@link ProcessorType#BLOCKING} has its conflict handling switched off here
     * instead of being refused, because it is the only layer that cannot switch it off itself: an annotation
     * attribute has no value for "handle no conflicts", while a named config and the global properties both
     * say it in one call. Every other contradiction travels on to {@link IdempotencyConfig#validate()} and is
     * refused there - by then it was written by a layer that had the words to write it differently.
     */
    private IdempotencyConfig merge(IdempotencyConfig config, RawOperationMetadata rawMetadata) {
        IdempotencyConfig.Builder configBuilder = IdempotencyConfig.builder(config);

        ProcessorType processorType = ProcessorTypeToggle.toProcessorType(rawMetadata.getProcessorType());
        if (processorType != null) {
            configBuilder.processorType(processorType);
        }

        mergeHeaderName(configBuilder, rawMetadata);
        mergeTtl(configBuilder, rawMetadata);
        mergeLockTtl(configBuilder, rawMetadata);
        mergeConflict(config, configBuilder, rawMetadata);
        mergeResponse(config, configBuilder, rawMetadata, processorType);
        mergeFingerprint(config, configBuilder, rawMetadata);

        if (ProcessorType.BLOCKING.equals(processorType)) {
            // only an inherited lock ttl is dropped: one this call site named itself is left for validate() to refuse
            if (rawMetadata.getLockTtl() == null) {
                configBuilder.lockTtl(null);
            }

            configBuilder.conflict(ConflictConfig.disabled());
            logDroppedConflictHandling(config.getConflictConfig());
        }

        IdempotencyConfig mergedConfig = configBuilder.build();
        mergedConfig.validate();

        return mergedConfig;
    }

    private void mergeHeaderName(IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        String headerName = rawMetadata.getHeaderName();
        if (!StringUtils.isBlank(headerName) && rawMetadata.useHeaderName()) {
            builder.headerName(headerName);
        }

        if (!rawMetadata.useHeaderName()) {
            builder.headerName(null);
        }
    }

    private void mergeTtl(IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        Duration ttl = rawMetadata.getTtl();
        if (ttl != null && !ttl.isNegative()) {
            builder.ttl(ttl);
        }
    }

    private void mergeLockTtl(IdempotencyConfig.Builder builder, RawOperationMetadata rawMetadata) {
        Duration lockTtl = rawMetadata.getLockTtl();
        if (lockTtl != null && !lockTtl.isNegative()) {
            builder.lockTtl(lockTtl);
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

    private void logDroppedConflictHandling(ConflictConfig conflictConfig) {
        if (conflictConfig != null && Boolean.TRUE.equals(conflictConfig.isEnabled())) {
            log.info("Conflict handling (strategy={}) is dropped: {} leaves a duplicate no conflict to arrive at",
                    conflictConfig.getStrategy(), ProcessorType.BLOCKING);
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

    private void mergeResponse(IdempotencyConfig config, IdempotencyConfig.Builder builder,
                               RawOperationMetadata rawMetadata, ProcessorType processorType) {

        ResponseConfig.Builder responseConfigBuilder = ResponseConfig.builder(config.getResponseConfig());

        if (ProcessorType.BLOCKING.equals(processorType)) {
            rejectReleasingChosenByCallSite(rawMetadata);
            logDroppedReleaseFor(config.getResponseConfig());
            builder.response(responseConfigBuilder.releaseFor(List.of(Throwable.class)).build());
            return;
        }

        Boolean useReleaseFor = Toggle.toBoolean(rawMetadata.getReleaseForToggle());
        List<Class<? extends Throwable>> releaseFor = rawMetadata.getReleaseFor();

        if (Boolean.FALSE.equals(useReleaseFor)) {
            responseConfigBuilder.releaseFor(List.of());
        } else if (releaseFor != null) {
            responseConfigBuilder.releaseFor(releaseFor);
        } else if (Boolean.TRUE.equals(useReleaseFor) && ResponseConfig.releasesForNothing(config.getResponseConfig())) {
            responseConfigBuilder.releaseFor(
                    ResponseConfig.defaultReleaseFor(defaultConfig.getResponseConfig())
            );
        }

        builder.response(responseConfigBuilder.build());
    }

    private void rejectReleasingChosenByCallSite(RawOperationMetadata rawMetadata) {
        if (!Toggle.UNSELECTED.equals(rawMetadata.getReleaseForToggle())) {
            throw new IllegalStateException(
                    "useReleaseFor cannot be set when processorType is %s: a rollback frees the key on any failure"
                            .formatted(ProcessorType.BLOCKING)
            );
        }
    }

    private void logDroppedReleaseFor(ResponseConfig responseConfig) {
        if (responseConfig == null || responseConfig.getReleaseFor() == null) {
            return;
        }

        if (!responseConfig.getReleaseFor().contains(Throwable.class)) {
            log.info("Release-for list ({}) is dropped: {} frees the key on any failure",
                    responseConfig.getReleaseFor(), ProcessorType.BLOCKING);
        }
    }

    private OperationMetadata buildMetadata(IdempotencyConfig config) {
        return DefaultOperationMetadata.builder()
                .headerName(config.getHeaderName())
                .ttl(config.getTtl())
                .lockTtl(config.getLockTtl())
                .processorType(config.getProcessorType())
                .conflictHandler(conflictHandlerProvider.provide(config.getConflictConfig()))
                .fingerprintPolicy(fingerprintPolicyProvider.provide(config.getFingerprintConfig()))
                .responseConfig(config.getResponseConfig())
                .build();
    }
}
