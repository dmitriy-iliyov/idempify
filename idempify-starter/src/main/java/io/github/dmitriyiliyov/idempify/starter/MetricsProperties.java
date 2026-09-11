package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

/**
 * Holds {@code idempify.metrics.*} - whether the counters of the metrics module are registered.
 * <p>
 * Observation is asked for, not assumed: the switch is off by default, so a registry the application already
 * has does not start collecting series nobody wanted. Switched on, {@code idempify-metrics} contributes the
 * two listeners the core otherwise fills with its own no-op ones - the idempotency event listener and the
 * cache event listener.
 * <p>
 * Switching it on without the module on the classpath leaves both listeners unfilled: the core supplies its
 * no-op ones only while this property is off, so nothing declares them and the context fails to start.
 */
public final class MetricsProperties {

    private final Boolean enabled;

    public MetricsProperties(@DefaultValue(IdempifyDefaults.METRICS_ENABLED_VALUE) Boolean enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
    }

    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "MetricsProperties{enabled=" + enabled + '}';
    }
}
