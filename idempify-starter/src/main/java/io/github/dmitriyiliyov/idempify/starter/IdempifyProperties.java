package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.StringUtils;
import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * Holds the {@code idempify.*} properties - the least specific configuration source, applying to every
 * intercepted call that does not decide the setting for itself.
 * <p>
 * {@code idempify.enabled} comes before all of them: switched off, the library describes nothing, so every
 * block is dropped rather than kept for a call site that will never run - and nothing below is checked
 * either, since an application that turned idempotency off owes no answer for a ttl nobody reads.
 * <p>
 * Two sections are refused outright when {@code idempify.processor-type} is
 * {@link ProcessorType#TRANSACTIONAL}: conflict handling, which that processor never reaches, and keeping a
 * 4xx or a 5xx response, which it cannot promise because the response is written in the operation's own
 * transaction. Either flag on its own is enough to fail startup.
 */
@ConfigurationProperties(prefix = "idempify")
public final class IdempifyProperties implements IdempotencyConfigProvider {

    private final Boolean enabled;
    private final String headerName;
    private final Duration ttl;
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
            this.conflict = Objects.requireNonNull(conflict, "conflict cannot be null");
            this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
            this.response = Objects.requireNonNull(response, "response cannot be null");
            this.cache = Objects.requireNonNull(cache, "cache cannot be null");
            this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
            rejectSectionsTheTransactionalProcessorCannotUse();
        } else {
            this.headerName = null;
            this.ttl = null;
            this.processorType = null;
            this.conflict = null;
            this.fingerprint = null;
            this.response = null;
            this.cache = null;
            this.metrics = null;
        }
    }

    private void rejectSectionsTheTransactionalProcessorCannotUse() {
        if (!ProcessorType.TRANSACTIONAL.equals(processorType)) {
            return;
        }

        if (Boolean.TRUE.equals(conflict.isEnabled())) {
            throw new IllegalStateException("""
                'idempify.conflict.enabled' must be false when 'idempify.processor-type' is TRANSACTIONAL: the transactional 
                processor settles a duplicate on the store's own insert, so there is no conflict left for a handler to see
            """);
        }

        if (Boolean.TRUE.equals(response.getShouldCache4xx()) || Boolean.TRUE.equals(response.getShouldCache5xx())) {
            throw new IllegalStateException("""
                'idempify.response.should-cache-4xx' and 'idempify.response.should-cache-5xx' must be false when 
                'idempify.processor-type' is TRANSACTIONAL: the response is written in the same transaction as the 
                operation, so a cache in front of it would serve answers a rollback has already taken back
            """);
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
                .processorType(processorType);

        builder.conflict(conflict.isEnabled()
                ? conflict.toConflictConfig()
                : ConflictConfig.disabled()
        );

        builder.fingerprint(fingerprint.isEnabled()
                ? fingerprint.toFingerprintConfig()
                : FingerprintConfig.disabled()
        );

        builder.response(response.toResponseConfig());

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
                ", processorType=" + processorType +
                ", conflict=" + conflict +
                ", fingerprint=" + fingerprint +
                ", response=" + response +
                ", cache=" + cache +
                ", metrics=" + metrics +
                '}';
    }
}
