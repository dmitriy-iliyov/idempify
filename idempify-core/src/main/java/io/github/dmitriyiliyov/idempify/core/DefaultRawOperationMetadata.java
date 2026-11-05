package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class DefaultRawOperationMetadata implements RawOperationMetadata {

    private final boolean useHeaderName;
    private final String headerName;
    private final Duration ttl;
    private final Duration lockTtl;
    private final ProcessorTypeToggle processorType;
    private final ConflictHandleStrategyToggle conflictHandleStrategy;
    private final Toggle fingerprintToggle;
    private final Toggle recordForToggle;
    private final List<Class<? extends Throwable>> recordFor;

    private DefaultRawOperationMetadata(Builder builder) {
        this.useHeaderName = builder.useHeaderName;
        this.headerName = builder.headerName;
        this.ttl = builder.ttl < 0 ? null : Duration.of(builder.ttl, builder.timeUnit.toChronoUnit());
        this.lockTtl = builder.lockTtl < 0 ? null : Duration.of(builder.lockTtl, builder.timeUnit.toChronoUnit());
        this.processorType = builder.processorType;
        this.conflictHandleStrategy = builder.conflictHandleStrategy;
        this.fingerprintToggle = builder.fingerprintToggle;
        this.recordForToggle = resolveRecordForToggle(builder);
        this.recordFor = builder.recordFor;
    }

    private static Toggle resolveRecordForToggle(Builder builder) {
        if (builder.recordFor == null) {
            return builder.recordForToggle;
        }

        if (Toggle.DISABLE.equals(builder.recordForToggle)) {
            throw new IllegalStateException(
                    "recordFor (%s) names the exceptions to keep on the record, so recordForToggle cannot be %s"
                            .formatted(builder.recordFor, builder.recordForToggle)
            );
        }

        return Toggle.ENABLE;
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
    public Duration getLockTtl() {
        return lockTtl;
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
    public Toggle getRecordForToggle() {
        return recordForToggle;
    }

    @Override
    public List<Class<? extends Throwable>> getRecordFor() {
        return recordFor;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DefaultRawOperationMetadata that = (DefaultRawOperationMetadata) o;
        return useHeaderName == that.useHeaderName
                && Objects.equals(headerName, that.headerName)
                && Objects.equals(ttl, that.ttl)
                && Objects.equals(lockTtl, that.lockTtl)
                && processorType == that.processorType
                && conflictHandleStrategy == that.conflictHandleStrategy
                && fingerprintToggle == that.fingerprintToggle
                && recordForToggle == that.recordForToggle
                && Objects.equals(recordFor, that.recordFor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(useHeaderName, headerName, ttl, lockTtl, processorType, conflictHandleStrategy,
                fingerprintToggle, recordFor, recordForToggle);
    }

    @Override
    public String toString() {
        return "DefaultRawOperationMetadata{" +
                "useHeaderName=" + useHeaderName +
                ", headerName='" + headerName + '\'' +
                ", ttl=" + ttl +
                ", lockTtl=" + lockTtl +
                ", processorType=" + processorType +
                ", conflictHandleStrategy=" + conflictHandleStrategy +
                ", fingerprintToggle=" + fingerprintToggle +
                ", recordFor=" + recordFor +
                ", recordForToggle=" + recordForToggle +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Boolean useHeaderName = true;
        private String headerName;
        private long ttl = -1;
        private long lockTtl = -1;
        private ProcessorTypeToggle processorType = ProcessorTypeToggle.UNSELECTED;
        private TimeUnit timeUnit = TimeUnit.HOURS;
        private ConflictHandleStrategyToggle conflictHandleStrategy = ConflictHandleStrategyToggle.UNSELECTED;
        private Toggle fingerprintToggle = Toggle.UNSELECTED;
        private Toggle recordForToggle = Toggle.UNSELECTED;
        private List<Class<? extends Throwable>> recordFor;

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

        public Builder lockTtl(long lockTtl) {
            this.lockTtl = lockTtl < 0 ? -1 : lockTtl;
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

        public Builder recordForToggle(Toggle recordForToggle) {
            this.recordForToggle = toggleOrDefault(recordForToggle);
            return this;
        }

        public Builder recordFor(List<Class<? extends Throwable>> recordFor) {
            this.recordFor = recordFor == null || recordFor.isEmpty() ? null : List.copyOf(recordFor);
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
