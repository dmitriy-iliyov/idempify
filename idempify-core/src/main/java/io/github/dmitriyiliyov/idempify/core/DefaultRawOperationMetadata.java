package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class DefaultRawOperationMetadata implements RawOperationMetadata {

    private final boolean useHeaderName;
    private final String headerName;
    private final Duration ttl;
    private final ProcessorTypeToggle processorType;
    private final ConflictHandleStrategyToggle conflictHandleStrategy;
    private final Toggle fingerprintToggle;
    private final Toggle cacheToggle;
    private final Toggle cache4xxToggle;
    private final Toggle cache5xxToggle;

    private DefaultRawOperationMetadata(Builder builder) {
        this.useHeaderName = builder.useHeaderName;
        this.headerName = builder.headerName;
        this.ttl = builder.ttl < 0 ? null : Duration.of(builder.ttl, builder.timeUnit.toChronoUnit());
        this.processorType = builder.processorType;
        this.conflictHandleStrategy = builder.conflictHandleStrategy;
        this.fingerprintToggle = builder.fingerprintToggle;
        this.cacheToggle = builder.cacheToggle;
        this.cache4xxToggle = builder.cache4xxToggle;
        this.cache5xxToggle = builder.cache5xxToggle;
    }

    @Override
    public boolean useHeaderName() {
        return useHeaderName;
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public Duration getTtl() {
        return ttl;
    }

    @Override
    public ProcessorTypeToggle getProcessorType() {
        return processorType;
    }

    @Override
    public ConflictHandleStrategyToggle getConflictHandleStrategy() {
        return conflictHandleStrategy;
    }

    @Override
    public Toggle getFingerprintToggle() {
        return fingerprintToggle;
    }

    @Override
    public Toggle getCacheToggle() {
        return cacheToggle;
    }

    @Override
    public Toggle getCache4xxToggle() {
        return cache4xxToggle;
    }

    @Override
    public Toggle getCache5xxToggle() {
        return cache5xxToggle;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DefaultRawOperationMetadata that = (DefaultRawOperationMetadata) o;
        return useHeaderName == that.useHeaderName
                && Objects.equals(headerName, that.headerName)
                && Objects.equals(ttl, that.ttl)
                && processorType == that.processorType
                && conflictHandleStrategy == that.conflictHandleStrategy
                && fingerprintToggle == that.fingerprintToggle
                && cacheToggle == that.cacheToggle
                && cache4xxToggle == that.cache4xxToggle
                && cache5xxToggle == that.cache5xxToggle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(useHeaderName, headerName, ttl, processorType, conflictHandleStrategy, fingerprintToggle,
                cacheToggle, cache4xxToggle, cache5xxToggle);
    }

    @Override
    public String toString() {
        return "DefaultRawOperationMetadata{" +
                "useHeaderName=" + useHeaderName +
                ", headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", processorType=" + processorType +
                ", conflictHandleStrategy=" + conflictHandleStrategy +
                ", fingerprintToggle=" + fingerprintToggle +
                ", cacheToggle=" + cacheToggle +
                ", cache4xxToggle=" + cache4xxToggle +
                ", cache5xxToggle=" + cache5xxToggle +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Boolean useHeaderName = true;
        private String headerName;
        private long ttl = -1;
        private ProcessorTypeToggle processorType = ProcessorTypeToggle.UNSELECTED;
        private TimeUnit timeUnit = TimeUnit.HOURS;
        private ConflictHandleStrategyToggle conflictHandleStrategy = ConflictHandleStrategyToggle.UNSELECTED;
        private Toggle fingerprintToggle = Toggle.UNSELECTED;
        private Toggle cacheToggle = Toggle.UNSELECTED;
        private Toggle cache4xxToggle = Toggle.UNSELECTED;
        private Toggle cache5xxToggle = Toggle.UNSELECTED;

        private Builder() {}

        public Builder useHeaderName(boolean useHeaderName) {
            this.useHeaderName = useHeaderName;
            return this;
        }

        public Builder headerName(String headerName) {
            this.headerName = headerName == null || headerName.isBlank() ? null : headerName.strip();
            return this;
        }

        public Builder ttl(long ttl) {
            this.ttl = ttl < 0 ? -1 : ttl;
            return this;
        }

        public Builder timeUnit(TimeUnit timeUnit) {
            this.timeUnit = Objects.requireNonNull(timeUnit, "timeUnit cannot be null");
            return this;
        }

        public Builder processorType(ProcessorTypeToggle processorType) {
            this.processorType = processorType == null ? ProcessorTypeToggle.UNSELECTED : processorType;
            return this;
        }

        public Builder conflictHandleStrategy(ConflictHandleStrategyToggle conflictHandleStrategy) {
            this.conflictHandleStrategy = conflictHandleStrategy == null
                    ? ConflictHandleStrategyToggle.UNSELECTED
                    : conflictHandleStrategy;
            return this;
        }

        public Builder fingerprintToggle(Toggle fingerprintToggle) {
            this.fingerprintToggle = toggleOrDefault(fingerprintToggle);
            return this;
        }

        public Builder cacheToggle(Toggle cacheToggle) {
            this.cacheToggle = toggleOrDefault(cacheToggle);
            return this;
        }

        public Builder cache4xxToggle(Toggle cache4xxToggle) {
            this.cache4xxToggle = toggleOrDefault(cache4xxToggle);
            return this;
        }

        public Builder cache5xxToggle(Toggle cache5xxToggle) {
            this.cache5xxToggle = toggleOrDefault(cache5xxToggle);
            return this;
        }

        public RawOperationMetadata build() {
            return new DefaultRawOperationMetadata(this);
        }

        private static Toggle toggleOrDefault(Toggle toggle) {
            return toggle == null ? Toggle.UNSELECTED : toggle;
        }
    }
}
