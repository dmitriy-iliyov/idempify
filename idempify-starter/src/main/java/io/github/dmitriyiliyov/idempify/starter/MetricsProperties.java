package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

/**
 * Holds {@code idempify.metrics.*} - whether the counters of the metrics module are registered.
 * <p>
 * Observation is asked for, not assumed: the switch is off by default, so a registry the application already
 * has does not start collecting series nobody wanted. Only {@code idempify-metrics} reads it, to decide
 * whether it contributes its listener and cache decorator. With the module absent from the classpath the
 * property is read by nobody, and that is the same silence as leaving it off.
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
