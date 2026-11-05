package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides what of a call's outcome stays on its record: which failures are kept and replayed instead of
 * releasing the key, and which headers of a recorded response survive.
 * <p>
 * A failure that is an instance of a type in {@link #getRecordFor()} is kept on the record and answers the
 * repeats; any other failure removes the record, so the key may run again. An empty list keeps nothing: every
 * failure releases the key. Only the non-blocking branch keeps a failure: in the blocking branch the rollback
 * takes the record away whatever was thrown, so it takes an empty list only.
 * <p>
 * A <em>partial</em> description: every getter may return {@code null}, meaning this layer does not decide the
 * setting - whatever the builder is not told stays {@code null}. The built-in answer is applied by
 * {@link #defaults()}, not by the builder.
 * <p>
 * An undecided {@code recordFor} travels through every layer as {@code null}, because the answer depends on the
 * processor that is finally chosen: {@link IdempifyDefaults#recordForOf} gives it once the last layer has spoken.
 * An empty list is a decision, not silence.
 */
public final class ResponseConfig implements ResponseProvidePolicy {

    private final List<Class<? extends Throwable>> recordFor;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    private ResponseConfig(Builder builder) {
        this.recordFor = builder.recordFor;
        this.includedHeaders = normalize(builder.includedHeaders);
        this.excludedHeaders = normalize(builder.excludedHeaders);
    }

    private Set<String> normalize(Set<String> headers) {
        if (headers != null && !headers.isEmpty()) {
            return headers.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }
        return headers;
    }

    public List<Class<? extends Throwable>> getRecordFor() {
        return recordFor;
    }

    @Override
    public Set<String> getIncludedHeaders() {
        return includedHeaders;
    }

    @Override
    public Set<String> getExcludedHeaders() {
        return excludedHeaders;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResponseConfig that = (ResponseConfig) o;
        return Objects.equals(recordFor, that.recordFor) &&
                Objects.equals(includedHeaders, that.includedHeaders) &&
                Objects.equals(excludedHeaders, that.excludedHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordFor, includedHeaders, excludedHeaders);
    }

    @Override
    public String toString() {
        return "ResponseConfig{" +
                "recordFor=" + recordFor +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }

    /**
     * Names every setting, {@code recordFor} included: an empty list, which is a decision to keep no failure
     * and not an undecided one.
     */
    public static ResponseConfig defaults() {
        return builder()
                .recordFor(List.of())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ResponseConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}: a setting the target decided wins, one it left {@code null}
     * keeps the reference's value.
     * <p>
     * An empty list or set counts as a decision rather than as silence: it is how a narrower layer says
     * "keep no failure" or "keep every header" over a reference that named a few.
     */
    public static ResponseConfig merge(ResponseConfig reference, ResponseConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        ResponseConfig.Builder configBuilder = builder(reference);

        List<Class<? extends Throwable>> recordFor = target.getRecordFor();
        if (recordFor != null) {
            configBuilder.recordFor(recordFor);
        }

        Set<String> includedHeaders = target.getIncludedHeaders();
        if (includedHeaders != null) {
            configBuilder.includedHeaders(includedHeaders);
        }

        Set<String> excludedHeaders = target.getExcludedHeaders();
        if (excludedHeaders != null) {
            configBuilder.excludedHeaders(excludedHeaders);
        }

        return configBuilder.build();
    }

    public static boolean recordsNothing(ResponseConfig responseConfig) {
        return responseConfig == null
                || responseConfig.getRecordFor() == null
                || responseConfig.getRecordFor().isEmpty();
    }

    public static List<Class<? extends Throwable>> defaultRecordFor(ResponseConfig responseConfig) {
        if (!recordsNothing(responseConfig)) {
            return responseConfig.getRecordFor();
        }
        return List.of();
    }

    public static final class Builder {

        private List<Class<? extends Throwable>> recordFor;
        private Set<String> includedHeaders;
        private Set<String> excludedHeaders;

        private Builder() {}

        private Builder(ResponseConfig config) {
            this.recordFor = config.recordFor;
            this.includedHeaders = config.includedHeaders;
            this.excludedHeaders = config.excludedHeaders;
        }

        public Builder recordFor(List<Class<? extends Throwable>> recordFor) {
            this.recordFor = List.copyOf(Objects.requireNonNull(recordFor, "recordFor cannot be null"));
            return this;
        }

        public Builder includedHeaders(Set<String> includedHeaders) {
            this.includedHeaders = Objects.requireNonNull(includedHeaders, "includedHeaders cannot be null");
            return this;
        }

        public Builder excludedHeaders(Set<String> excludedHeaders) {
            this.excludedHeaders = Objects.requireNonNull(excludedHeaders, "excludedHeaders cannot be null");
            return this;
        }

        public ResponseConfig build() {
            return new ResponseConfig(this);
        }
    }
}
