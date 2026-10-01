package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.StringUtils;
import io.github.dmitriyiliyov.idempify.core.config.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Holds the {@code idempify.*} properties - the least specific configuration source, applying to every
 * intercepted call that does not decide the setting for itself.
 * <p>
 * {@code idempify.enabled} comes before all of them: switched off, the library describes nothing, so every
 * block is dropped rather than kept for a call site that will never run - and nothing below is checked
 * either, since an application that turned idempotency off owes no answer for a ttl nobody reads.
 * <p>
 * Conflict handling and a lock ttl are refused outright when {@code idempify.processor-type} is
 * {@link ProcessorType#TRANSACTIONAL_BLOCKING}: that processor never reaches a conflict and claims the key inside the
 * business transaction, so either setting on its own is enough to fail startup. Under
 * {@link ProcessorType#TRANSACTIONAL_NON_BLOCKING} it is the other way round for conflict handling: a duplicate meets the
 * unfinished operation there, so switching the handling off fails startup too.
 */
@ConfigurationProperties(prefix = "idempify")
public final class IdempifyProperties implements IdempotencyConfigProvider {

    private final Boolean enabled;
    private final String headerName;
    private final Duration ttl;
    private final Duration lockTtl;
    private final ProcessorType processorType;
    @NestedConfigurationProperty
    private final ConflictProperties conflict;
    @NestedConfigurationProperty
    private final FingerprintProperties fingerprint;
    @NestedConfigurationProperty
    private final ResponseProperties response;
    @NestedConfigurationProperty
    private final CacheProperties cache;
    @NestedConfigurationProperty
    private final MetricsProperties metrics;

    public IdempifyProperties(@DefaultValue(IdempifyDefaults.ENABLED_VALUE) Boolean enabled,
                              @DefaultValue(IdempifyDefaults.HEADER_NAME) String headerName,
                              @DefaultValue(IdempifyDefaults.TTL_VALUE) Duration ttl,
                              Duration lockTtl,
                              @DefaultValue(IdempifyDefaults.PROCESSOR_TYPE_NAME) ProcessorType processorType,
                              @DefaultValue ConflictProperties conflict,
                              @DefaultValue FingerprintProperties fingerprint,
                              @DefaultValue ResponseProperties response,
                              @DefaultValue CacheProperties cache,
                              @DefaultValue MetricsProperties metrics) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");

        if (enabled) {
            if (StringUtils.isBlank(headerName)) {
                throw new IllegalArgumentException("headerName cannot be null, empty or blank");
            }
            this.headerName = headerName.strip();

            Objects.requireNonNull(ttl, "ttl cannot be null");
            if (!ttl.isPositive()) {
                throw new IllegalArgumentException("ttl must be positive");
            }
            this.ttl = ttl;

            this.processorType = Objects.requireNonNull(processorType, "processorType cannot be null");
            this.lockTtl = resolveLockTtl(lockTtl, this.processorType);
            this.conflict = Objects.requireNonNull(conflict, "conflict cannot be null");
            this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
            this.response = Objects.requireNonNull(response, "response cannot be null");
            this.cache = Objects.requireNonNull(cache, "cache cannot be null");
            this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
            rejectSectionsTheBlockingProcessorCannotUse();
            rejectSectionsTheNonBlockingProcessorCannotLack();
        } else {
            this.headerName = null;
            this.ttl = null;
            this.lockTtl = null;
            this.processorType = null;
            this.conflict = null;
            this.fingerprint = null;
            this.response = null;
            this.cache = null;
            this.metrics = null;
        }
    }

    /**
     * The blocking processor holds its claim in the business transaction, which commits or rolls the
     * record back with it, so there is no claim of its own to give a lifetime to - a lock ttl named
     * there is refused rather than substituted, because this layer is one that can leave it unsaid.
     * Left unsaid anywhere else, it gets the built-in answer.
     */
    private static Duration resolveLockTtl(Duration lockTtl, ProcessorType processorType) {
        if (ProcessorType.TRANSACTIONAL_BLOCKING.equals(processorType)) {
            if (lockTtl != null) {
                throw new IllegalStateException("""
                        'idempify.lock-ttl' cannot be set when 'idempify.processor-type' is BLOCKING: \
                        that processor claims the key inside the business transaction, so there is no \
                        separate claim to hold for a while""");
            }
            return null;
        }

        Duration resolved = lockTtl == null ? Duration.parse(IdempifyDefaults.LOCK_TTL_VALUE) : lockTtl;
        if (!resolved.isPositive()) {
            throw new IllegalArgumentException("lockTtl must be positive");
        }
        return resolved;
    }

    private void rejectSectionsTheNonBlockingProcessorCannotLack() {
        if (!ProcessorType.TRANSACTIONAL_NON_BLOCKING.equals(processorType)) {
            return;
        }

        if (Boolean.FALSE.equals(conflict.isEnabled())) {
            throw new IllegalStateException("""
                    'idempify.conflict.enabled' must be true when 'idempify.processor-type' is NON_BLOCKING: \
                    a duplicate meets the unfinished operation there, and only a conflict handler can answer it""");
        }
    }

    private void rejectSectionsTheBlockingProcessorCannotUse() {
        if (!ProcessorType.TRANSACTIONAL_BLOCKING.equals(processorType)) {
            return;
        }

        if (Boolean.TRUE.equals(conflict.isEnabled())) {
            throw new IllegalStateException("""
                    'idempify.conflict.enabled' must be false when 'idempify.processor-type' is BLOCKING: \
                    the blocking processor settles a duplicate on the store's own insert, so there is no \
                    conflict left for a handler to see""");
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if {@code idempify.enabled} is false - a switched-off library holds no
     * settings to hand over, and whoever is asking got past a condition that should have kept them out.
     */
    @Override
    public IdempotencyConfig provide() {
        if (!enabled) {
            throw new IllegalStateException(
                    "'idempify.enabled' is false, so there is no configuration to provide: set it to true to configure idempotency"
            );
        }

        IdempotencyConfig.Builder builder = IdempotencyConfig.builder()
                .headerName(headerName)
                .ttl(ttl)
                .lockTtl(lockTtl)
                .processorType(processorType);

        builder.conflict(conflict.isEnabled()
                ? conflict.toConflictConfig()
                : ConflictConfig.disabled()
        );

        builder.fingerprint(fingerprint.isEnabled()
                ? fingerprint.toFingerprintConfig()
                : FingerprintConfig.disabled()
        );

        builder.response(ProcessorType.TRANSACTIONAL_BLOCKING.equals(processorType)
                ? ResponseConfig.builder(response.toResponseConfig()).releaseFor(List.of(Throwable.class)).build()
                : response.toResponseConfig()
        );

        IdempotencyConfig config = builder.build();
        config.validate();
        return config;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public Duration getTtl() {
        return ttl;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public ProcessorType getProcessorType() {
        return processorType;
    }

    public ConflictProperties getConflict() {
        return conflict;
    }

    public FingerprintProperties getFingerprint() {
        return fingerprint;
    }

    public ResponseProperties getResponse() {
        return response;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return "IdempifyProperties{" +
                "enabled=" + enabled +
                ", headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", lockTtl=" + lockTtl +
                ", processorType=" + processorType +
                ", conflict=" + conflict +
                ", fingerprint=" + fingerprint +
                ", response=" + response +
                ", cache=" + cache +
                ", metrics=" + metrics +
                '}';
    }
}
