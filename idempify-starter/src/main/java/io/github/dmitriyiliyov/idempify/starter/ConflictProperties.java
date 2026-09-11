package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * Holds the {@code idempify.conflict.*} properties.
 * <p>
 * Settings that apply to a single strategy only live in their own nested block, so a configuration that
 * doesn't use that strategy simply omits the whole block.
 */
public final class ConflictProperties {

    private final Boolean enabled;
    private final ConflictHandleStrategy strategy;
    @NestedConfigurationProperty
    private final WaitConflictProperties wait;

    public ConflictProperties(@DefaultValue(IdempifyDefaults.CONFLICT_ENABLED_VALUE)
                              Boolean enabled,
                              @DefaultValue(IdempifyDefaults.CONFLICT_HANDLE_STRATEGY_VALUE)
                              ConflictHandleStrategy strategy,
                              WaitConflictProperties wait) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
        if (!ConflictHandleStrategy.WAIT.equals(strategy) && wait != null) {
            throw new IllegalStateException("wait should not be specified when strategy is %s".formatted(strategy));
        }
        this.wait = wait;
    }

    public ConflictConfig toConflictConfig() {
        ConflictConfig.Builder builder = ConflictConfig.builder()
                .enabled(enabled)
                .strategy(strategy);

        if (ConflictHandleStrategy.WAIT.equals(strategy)) {
            WaitConflictHandlerConfig config;
            if (wait == null) {
                config = WaitConflictHandlerConfig.defaults();
            } else {
                config = wait.toWaitConflictHandlerConfig();
            }
            builder.handlerConfig(config);
        } else {
            builder.handlerConfig(ConflictConfig.getDefaultHandlerConfig(strategy));
        }

        return builder.build();
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public ConflictHandleStrategy getStrategy() {
        return strategy;
    }

    public WaitConflictProperties getWait() {
        return wait;
    }

    @Override
    public String toString() {
        return "ConflictProperties{" +
                "enabled=" + enabled +
                ", strategy=" + strategy +
                ", wait=" + wait +
                '}';
    }

    /**
     * Holds the {@code idempify.conflict.wait.*} properties — the polling backoff of the
     * {@link ConflictHandleStrategy#WAIT} strategy.
     * <p>
     * Every property is answered: an absent one falls back to its {@code @DefaultValue}, so this block never
     * hands the core a half-filled backoff.
     * <p>
     * What counts as a valid value is decided by {@link WaitConflictHandlerConfig.Builder}, which the
     * constructor calls while the properties bind - so a value it rejects fails the application's startup
     * rather than the first conflict.
     */
    public static final class WaitConflictProperties {

        private final Duration delay;
        private final Double multiplier;
        private final Integer maxAttempts;
        private final Duration maxDuration;
        private final WaitConflictHandlerConfig config;

        public WaitConflictProperties(@DefaultValue(IdempifyDefaults.WAIT_DELAY_VALUE) Duration delay,
                                      @DefaultValue(IdempifyDefaults.WAIT_MULTIPLIER_VALUE) Double multiplier,
                                      @DefaultValue(IdempifyDefaults.WAIT_MAX_ATTEMPTS_VALUE) Integer maxAttempts,
                                      @DefaultValue(IdempifyDefaults.WAIT_MAX_DURATION_VALUE) Duration maxDuration) {
            this.delay = Objects.requireNonNull(delay, "delay cannot be null");
            this.multiplier = Objects.requireNonNull(multiplier, "multiplier cannot be null");
            this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts cannot be null");
            this.maxDuration = Objects.requireNonNull(maxDuration, "maxDuration cannot be null");
            this.config = WaitConflictHandlerConfig.builder()
                    .delay(delay)
                    .multiplier(multiplier)
                    .maxAttempts(maxAttempts)
                    .maxDuration(maxDuration)
                    .build();
        }

        public WaitConflictHandlerConfig toWaitConflictHandlerConfig() {
            return config;
        }

        public Duration getDelay() {
            return delay;
        }

        public Double getMultiplier() {
            return multiplier;
        }

        public Integer getMaxAttempts() {
            return maxAttempts;
        }

        public Duration getMaxDuration() {
            return maxDuration;
        }

        @Override
        public String toString() {
            return "WaitConflictProperties{" +
                    "delay=" + delay +
                    ", multiplier=" + multiplier +
                    ", maxAttempts=" + maxAttempts +
                    ", maxDuration=" + maxDuration +
                    '}';
        }
    }
}
