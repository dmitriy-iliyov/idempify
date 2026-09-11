package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which answers of a completed operation are kept on its record, and which headers of them survive.
 * <p>
 * A response that is not kept is not a lost cache entry: the operation itself is recorded either way, and it
 * is the response - the status, the body, the headers - that this config decides about. What a repeat call
 * gets when the record carries no response is decided by the transport, not here.
 * <p>
 * A <em>partial</em> description: every getter may return {@code null}, meaning this layer does not decide the
 * flag - whatever the builder is not told stays {@code null}. The {@code DEFAULT_*} constants are applied by
 * {@link #defaults()}, not by the builder.
 * <p>
 * How long an entry lives is not settable here: a cached result is only useful while the operation behind it
 * is still on record, so its lifetime is the operation's own - the writer derives it from when the repository
 * says the operation expires.
 */
public final class ResponseConfig implements ResponseProvidePolicy {

    public static final Logger log = LoggerFactory.getLogger(ResponseConfig.class);
    public static final boolean DEFAULT_SHOULD_CACHE_4XX = Boolean.parseBoolean(IdempifyDefaults.RESPONSE_CACHE_4XX_VALUE);
    public static final boolean DEFAULT_SHOULD_CACHE_5XX = Boolean.parseBoolean(IdempifyDefaults.RESPONSE_CACHE_5XX_VALUE);

    private final Boolean shouldCache4xx;
    private final Boolean shouldCache5xx;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    private ResponseConfig(Builder builder) {
        this.shouldCache4xx = builder.shouldCache4xx;
        this.shouldCache5xx = builder.shouldCache5xx;
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

    public Boolean shouldCache4xx() {
        return shouldCache4xx;
    }

    public Boolean shouldCache5xx() {
        return shouldCache5xx;
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
        return Objects.equals(shouldCache4xx, that.shouldCache4xx) &&
                Objects.equals(shouldCache5xx, that.shouldCache5xx) &&
                Objects.equals(includedHeaders, that.includedHeaders) &&
                Objects.equals(excludedHeaders, that.excludedHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shouldCache4xx, shouldCache5xx, includedHeaders, excludedHeaders);
    }

    @Override
    public String toString() {
        return "ResponseConfig{" +
                "shouldCache4xx=" + shouldCache4xx +
                ", shouldCache5xx=" + shouldCache5xx +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }

    public static ResponseConfig defaults() {
        return builder()
                .shouldCache4xx(DEFAULT_SHOULD_CACHE_4XX)
                .shouldCache5xx(DEFAULT_SHOULD_CACHE_5XX)
                .build();
    }

    public static ResponseConfig all() {
        return builder()
                .shouldCache4xx(true)
                .shouldCache5xx(true)
                .build();
    }

    public static ResponseConfig disabled() {
        return builder()
                .shouldCache4xx(false)
                .shouldCache5xx(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ResponseConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}: a flag the target decided wins, one it left {@code null}
     * keeps the reference's value. A target that decided a flag to the same value as the built-in default is
     * a target that decided, and overrides.
     * <p>
     * Only the two flags are layered - the header sets come from {@code reference} whatever {@code target}
     * says about them.
     */
    // this isn;t place for shouldCacheXxx, shouldCacheXxx must deside saving to db or not when throwing
    public static ResponseConfig merge(ResponseConfig reference, ResponseConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        ResponseConfig.Builder configBuilder = builder(reference);

        Boolean shouldCache4xx = target.shouldCache4xx();
        if (shouldCache4xx != null) {
            configBuilder.shouldCache4xx(shouldCache4xx);
        }

        Boolean shouldCache5xx = target.shouldCache5xx();
        if (shouldCache5xx != null) {
            configBuilder.shouldCache5xx(shouldCache5xx);
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

    public static final class Builder {

        private Boolean shouldCache4xx;
        private Boolean shouldCache5xx;
        private Set<String> includedHeaders;
        private Set<String> excludedHeaders;

        private Builder() {}

        private Builder(ResponseConfig config) {
            this.shouldCache4xx = config.shouldCache4xx;
            this.shouldCache5xx = config.shouldCache5xx;
            this.includedHeaders = config.includedHeaders;
            this.excludedHeaders = config.excludedHeaders;
        }

        public Builder shouldCache4xx(boolean shouldCache4xx) {
            this.shouldCache4xx = shouldCache4xx;
            return this;
        }

        public Builder shouldCache5xx(boolean shouldCache5xx) {
            this.shouldCache5xx = shouldCache5xx;
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
