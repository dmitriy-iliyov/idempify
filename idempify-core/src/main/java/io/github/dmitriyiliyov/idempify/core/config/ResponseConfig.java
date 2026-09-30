package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides what of a call's outcome stays on its record: which failures release the key instead of being kept
 * and replayed, and which headers of a recorded response survive.
 * <p>
 * A failure that is an instance of a type in {@link #getReleaseFor()} removes the record, so the key may run
 * again; any other failure is kept on the record and answers the repeats. An empty list releases for nothing
 * and keeps every failure. Only the non-blocking branch keeps a failure: in the blocking branch the rollback
 * takes the record away whatever was thrown.
 * <p>
 * A <em>partial</em> description: every getter may return {@code null}, meaning this layer does not decide the
 * setting - whatever the builder is not told stays {@code null}. The built-in answer is applied by
 * {@link #defaults()}, not by the builder.
 */
public final class ResponseConfig implements ResponseProvidePolicy {

    private final List<Class<? extends Throwable>> releaseFor;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    private ResponseConfig(Builder builder) {
        this.releaseFor = builder.releaseFor;
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

    public List<Class<? extends Throwable>> getReleaseFor() {
        return releaseFor;
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
        return Objects.equals(releaseFor, that.releaseFor) &&
                Objects.equals(includedHeaders, that.includedHeaders) &&
                Objects.equals(excludedHeaders, that.excludedHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseFor, includedHeaders, excludedHeaders);
    }

    @Override
    public String toString() {
        return "ResponseConfig{" +
                "releaseFor=" + releaseFor +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }

    public static ResponseConfig defaults() {
        return builder()
                .releaseFor(IdempifyDefaults.DEFAULT_RELEASE_FOR_LIST)
                .build();
    }

    public static ResponseConfig all() {
        return builder()
                .releaseFor(List.of())
                .build();
    }

    public static ResponseConfig disabled() {
        return builder()
                .releaseFor(List.of(Throwable.class))
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
     * "release for nothing" or "keep every header" over a reference that named a few.
     */
    public static ResponseConfig merge(ResponseConfig reference, ResponseConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        ResponseConfig.Builder configBuilder = builder(reference);

        List<Class<? extends Throwable>> releaseFor = target.getReleaseFor();
        if (releaseFor != null) {
            configBuilder.releaseFor(releaseFor);
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

    public static boolean releasesForNothing(ResponseConfig responseConfig) {
        return responseConfig == null
                || responseConfig.getReleaseFor() == null
                || responseConfig.getReleaseFor().isEmpty();
    }

    public static List<Class<? extends Throwable>> defaultReleaseFor(ResponseConfig responseConfig) {
        if (!releasesForNothing(responseConfig)) {
            return responseConfig.getReleaseFor();
        }
        return IdempifyDefaults.DEFAULT_RELEASE_FOR_LIST;
    }

    public static final class Builder {

        private List<Class<? extends Throwable>> releaseFor;
        private Set<String> includedHeaders;
        private Set<String> excludedHeaders;

        private Builder() {}

        private Builder(ResponseConfig config) {
            this.releaseFor = config.releaseFor;
            this.includedHeaders = config.includedHeaders;
            this.excludedHeaders = config.excludedHeaders;
        }

        public Builder releaseFor(List<Class<? extends Throwable>> releaseFor) {
            this.releaseFor = List.copyOf(Objects.requireNonNull(releaseFor, "releaseFor cannot be null"));
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
