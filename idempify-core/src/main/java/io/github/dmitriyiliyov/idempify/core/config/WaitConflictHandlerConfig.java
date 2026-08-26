package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings of the {@link ConflictHandleStrategy#WAIT} handler, which polls the store until the operation that
 * won the key reaches its final state.
 * <p>
 * A <em>partial</em> description: every getter may return {@code null}, meaning this layer does not decide the
 * setting - whatever the builder is not told stays {@code null}. That is what a value equal to a
 * {@code DEFAULT_*} constant cannot express, which is why the constants are applied by {@link #defaults()} and
 * not by the builder.
 * <p>
 * By the time a config reaches {@code WaitConflictHandler} it must be complete - the handler unboxes every
 * setting. Keeping it so is the merge's job: the global layer answers everything, so
 * {@link #merge(ConflictHandlerConfig, ConflictHandlerConfig)} can only fill gaps, never open them.
 */
public final class WaitConflictHandlerConfig implements ConflictHandlerConfig {

    public static final long DEFAULT_DELAY_MILLIS = Duration.parse(IdempifyDefaults.WAIT_DELAY_VALUE).toMillis();
    public static final double DEFAULT_MULTIPLIER = Double.parseDouble(IdempifyDefaults.WAIT_MULTIPLIER_VALUE);
    public static final int DEFAULT_MAX_ATTEMPTS = Integer.parseInt(IdempifyDefaults.WAIT_MAX_ATTEMPTS_VALUE);
    public static final long DEFAULT_MAX_DURATION_MILLIS =
            Duration.parse(IdempifyDefaults.WAIT_MAX_DURATION_VALUE).toMillis();

    private final Long delay;
    private final Double multiplier;
    private final Integer maxAttempts;
    private final Long maxDuration;

    private WaitConflictHandlerConfig(Builder builder) {
        this.delay = builder.delay;
        this.multiplier = builder.multiplier;
        this.maxAttempts = builder.maxAttempts;
        this.maxDuration = builder.maxDuration;
    }

    public Long getDelay() {
        return delay;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public Long getMaxDuration() {
        return maxDuration;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WaitConflictHandlerConfig that = (WaitConflictHandlerConfig) o;
        return Objects.equals(delay, that.delay) &&
                Objects.equals(multiplier, that.multiplier) &&
                Objects.equals(maxAttempts, that.maxAttempts) &&
                Objects.equals(maxDuration, that.maxDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delay, multiplier, maxAttempts, maxDuration);
    }

    @Override
    public String toString() {
        return "WaitConflictHandlerConfig{" +
                "delay=" + delay +
                ", multiplier=" + multiplier +
                ", maxAttempts=" + maxAttempts +
                ", maxDuration=" + maxDuration +
                '}';
    }

    public static WaitConflictHandlerConfig defaults() {
        return builder()
                .delay(DEFAULT_DELAY_MILLIS)
                .multiplier(DEFAULT_MULTIPLIER)
                .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                .maxDuration(DEFAULT_MAX_DURATION_MILLIS)
                .build();
    }


    public boolean notEmpty() {
        return delay != null && multiplier != null && maxAttempts != null && maxDuration != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(WaitConflictHandlerConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}: a setting the target decided wins, one it left {@code null}
     * keeps the reference's value. A zero delay is a decision like any other and overrides - which is exactly
     * what reading "untouched" off the {@code DEFAULT_*} constants could not express.
     * <p>
     * Bounds are not re-checked here: a value only ever reaches a config through a builder setter, which
     * rejects an invalid one on the spot.
     * <p>
     * Both arguments must be {@code WaitConflictHandlerConfig} instances - the {@link ConflictHandlerConfig}
     * signature exists so {@link ConflictConfig#merge} can call this without casting the pair itself.
     */
    public static ConflictHandlerConfig merge(ConflictHandlerConfig reference, ConflictHandlerConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        if (!(reference instanceof WaitConflictHandlerConfig referenceConfig)) {
            throw new IllegalArgumentException(
                    "reference must be a WaitConflictHandlerConfig, but was %s".formatted(reference.getClass().getName())
            );
        }

        if (!(target instanceof WaitConflictHandlerConfig targetConfig)) {
            throw new IllegalArgumentException(
                    "target must be a WaitConflictHandlerConfig, but was %s".formatted(target.getClass().getName())
            );
        }

        WaitConflictHandlerConfig.Builder configBuilder = builder(referenceConfig);

        Long delay = targetConfig.getDelay();
        if (delay != null) {
            configBuilder.delay(delay);
        }

        Double multiplier = targetConfig.getMultiplier();
        if (multiplier != null) {
            configBuilder.multiplier(multiplier);
        }

        Integer maxAttempts = targetConfig.getMaxAttempts();
        if (maxAttempts != null) {
            configBuilder.maxAttempts(maxAttempts);
        }

        Long maxDuration = targetConfig.getMaxDuration();
        if (maxDuration != null) {
            configBuilder.maxDuration(maxDuration);
        }

        return configBuilder.build();
    }

    public static final class Builder {

        private Long delay;
        private Double multiplier;
        private Integer maxAttempts;
        private Long maxDuration;

        private Builder() {}

        private Builder(WaitConflictHandlerConfig config) {
            this.delay = config.delay;
            this.multiplier = config.multiplier;
            this.maxAttempts = config.maxAttempts;
            this.maxDuration = config.maxDuration;
        }

        public Builder delay(long delayMillis) {
            if (delayMillis < 1) {
                throw new IllegalArgumentException("delay cannot be less than 1");
            }
            this.delay = delayMillis;
            return this;
        }

        public Builder delay(Duration delay) {
            Objects.requireNonNull(delay, "delay cannot be null");
            return delay(delay.toMillis());
        }

        public Builder multiplier(double multiplier) {
            if (multiplier <= 0) {
                throw new IllegalArgumentException("multiplier must be positive");
            }
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts cannot be less than 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder maxDuration(long maxDurationMillis) {
            if (maxDurationMillis < 1) {
                throw new IllegalArgumentException("maxDuration cannot be less than 1");
            }
            this.maxDuration = maxDurationMillis;
            return this;
        }

        /**
         * Converts and hands over to {@link #maxDuration(long)} - the bound lives there alone, so the same
         * value cannot be accepted in millis and rejected as a {@link Duration}.
         */
        public Builder maxDuration(Duration maxDuration) {
            Objects.requireNonNull(maxDuration, "maxDuration cannot be null");
            return maxDuration(maxDuration.toMillis());
        }

        public WaitConflictHandlerConfig build() {
            return new WaitConflictHandlerConfig(this);
        }
    }
}
