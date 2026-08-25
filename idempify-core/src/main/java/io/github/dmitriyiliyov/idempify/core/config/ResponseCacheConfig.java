package io.github.dmitriyiliyov.idempify.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Decides which results of a completed operation are copied into the {@code ResponseCache} - the optional
 * secondary store that sits in front of the {@code OperationRepository}.
 * <p>
 * The repository is the source of truth and records every completed operation regardless of this config, so
 * turning caching off never changes behaviour: a repeat call carrying the same idempotency key still replays
 * the stored result, it just reads it from the database instead of the cache. What this config buys is
 * latency and database load, not correctness - the library works with no cache at all.
 * <p>
 * A <em>partial</em> description: every getter may return {@code null}, meaning this layer does not decide the
 * flag - whatever the builder is not told stays {@code null}. That is what a flag equal to a {@code DEFAULT_*}
 * constant cannot express, which is why the constants are applied by {@link #defaults()} and not by the
 * builder.
 * <p>
 * How long an entry lives is not settable here: a cached result is only useful while the operation behind it
 * is still on record, so its lifetime is the operation's own - the writer derives it from when the repository
 * says the operation expires.
 */
public final class ResponseCacheConfig {

    public static final Logger log = LoggerFactory.getLogger(ResponseCacheConfig.class);
    public static final boolean DEFAULT_SHOULD_CACHE = true;
    public static final boolean DEFAULT_SHOULD_CACHE_4XX = true;
    public static final boolean DEFAULT_SHOULD_CACHE_5XX = false;

    private final Boolean enabled;
    private final Boolean shouldCache4xx;
    private final Boolean shouldCache5xx;

    private ResponseCacheConfig(Builder builder) {
        this.enabled = builder.shouldCache;
        this.shouldCache4xx = builder.shouldCache4xx;
        this.shouldCache5xx = builder.shouldCache5xx;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Boolean shouldCache4xx() {
        return shouldCache4xx;
    }

    public Boolean shouldCache5xx() {
        return shouldCache5xx;
    }

    public void validate() {
        if (!enabled) {
            if (shouldCache4xx || shouldCache5xx) {
                throw new IllegalStateException("any properties shouldn't be enabled when responseCache is disabled");
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResponseCacheConfig that = (ResponseCacheConfig) o;
        return Objects.equals(enabled, that.enabled) &&
                Objects.equals(shouldCache4xx, that.shouldCache4xx) &&
                Objects.equals(shouldCache5xx, that.shouldCache5xx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, shouldCache4xx, shouldCache5xx);
    }

    @Override
    public String toString() {
        return "ResponseCacheConfig{" +
                "enabled=" + enabled +
                ", shouldCache4xx=" + shouldCache4xx +
                ", shouldCache5xx=" + shouldCache5xx +
                '}';
    }

    public static ResponseCacheConfig defaults() {
        return builder()
                .enabled(DEFAULT_SHOULD_CACHE)
                .shouldCache4xx(DEFAULT_SHOULD_CACHE_4XX)
                .shouldCache5xx(DEFAULT_SHOULD_CACHE_5XX)
                .build();
    }

    public static ResponseCacheConfig all() {
        return builder()
                .enabled(true)
                .shouldCache4xx(true)
                .shouldCache5xx(true)
                .build();
    }

    public static ResponseCacheConfig disabled() {
        return builder()
                .enabled(false)
                .shouldCache4xx(false)
                .shouldCache5xx(false)
                .build();
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ResponseCacheConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}: a flag the target decided wins, one it left {@code null}
     * keeps the reference's value. Caching 4xx, which happens to be the built-in default, is therefore a
     * decision like any other and overrides - which is exactly what reading "untouched" off the
     * {@code DEFAULT_*} constants could not express.
     * <p>
     * Switching caching off is one-way: whichever side says {@code enabled = false} settles it, and no
     * narrower layer can turn it back on. That is not a merge convention but the truth about the machinery -
     * the property that switches the cache off is the same one that keeps the backend and the caching filter
     * out of the context, so a call site allowed to "enable" caching would only produce metadata that lies.
     */
    public static ResponseCacheConfig merge(ResponseCacheConfig reference, ResponseCacheConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        if (Boolean.FALSE.equals(target.isEnabled())) {
            return ResponseCacheConfig.disabled();
        }

        if (Boolean.FALSE.equals(reference.isEnabled())) {
            log.warn("Cache cannot be using if reference config is disabled");
            return ResponseCacheConfig.disabled();
        }

        ResponseCacheConfig.Builder configBuilder = builder(reference);

        Boolean enabled = target.isEnabled();
        if (enabled != null) {
            configBuilder.enabled(enabled);
        }

        Boolean shouldCache4xx = target.shouldCache4xx();
        if (shouldCache4xx != null) {
            configBuilder.shouldCache4xx(shouldCache4xx);
        }

        Boolean shouldCache5xx = target.shouldCache5xx();
        if (shouldCache5xx != null) {
            configBuilder.shouldCache5xx(shouldCache5xx);
        }

        return configBuilder.build();
    }

    public static final class Builder {

        private Boolean shouldCache;
        private Boolean shouldCache4xx;
        private Boolean shouldCache5xx;

        private Builder() {}

        private Builder(ResponseCacheConfig config) {
            this.shouldCache = config.enabled;
            this.shouldCache4xx = config.shouldCache4xx;
            this.shouldCache5xx = config.shouldCache5xx;
        }

        public Builder enabled(boolean shouldCache) {
            this.shouldCache = shouldCache;
            return this;
        }

        public Builder shouldCache4xx(boolean shouldCache4xx) {
            this.shouldCache4xx = shouldCache4xx;
            return this;
        }

        public Builder shouldCache5xx(boolean shouldCache5xx) {
            this.shouldCache5xx = shouldCache5xx;
            return this;
        }

        public ResponseCacheConfig build() {
            return new ResponseCacheConfig(this);
        }
    }
}
