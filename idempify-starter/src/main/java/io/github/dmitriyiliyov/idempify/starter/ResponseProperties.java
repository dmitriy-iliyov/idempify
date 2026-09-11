package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;
import java.util.Set;

/**
 * Holds the {@code idempify.response.*} properties - which answers of a completed operation are kept on its
 * record, and which of their headers survive.
 * <p>
 * Every property is answered: an absent one falls back to its {@code @DefaultValue}, so this block hands the
 * core a fully decided response policy rather than a partial one for a call site to finish.
 * <p>
 * For the two header sets, naming nothing and naming an empty set say the same thing: neither narrows
 * anything, so both leave the response with the headers it carries.
 */
public final class ResponseProperties {

    private final Boolean shouldCache4xx;
    private final Boolean shouldCache5xx;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    public ResponseProperties(@DefaultValue(IdempifyDefaults.RESPONSE_CACHE_4XX_VALUE) Boolean shouldCache4xx,
                              @DefaultValue(IdempifyDefaults.RESPONSE_CACHE_5XX_VALUE) Boolean shouldCache5xx,
                              @DefaultValue Set<String> includedHeaders,
                              @DefaultValue Set<String> excludedHeaders) {
        this.shouldCache4xx = Objects.requireNonNull(shouldCache4xx, "shouldCache4xx cannot be null");
        this.shouldCache5xx = Objects.requireNonNull(shouldCache5xx, "shouldCache5xx cannot be null");
        this.includedHeaders = Objects.requireNonNull(includedHeaders, "includedHeaders cannot be null");
        this.excludedHeaders = Objects.requireNonNull(excludedHeaders, "excludedHeaders cannot be null");
    }

    public ResponseConfig toResponseConfig() {
        return ResponseConfig.builder()
                .shouldCache4xx(shouldCache4xx)
                .shouldCache5xx(shouldCache5xx)
                .includedHeaders(includedHeaders)
                .excludedHeaders(excludedHeaders)
                .build();
    }

    public Boolean getShouldCache4xx() {
        return shouldCache4xx;
    }

    public Boolean getShouldCache5xx() {
        return shouldCache5xx;
    }

    public Set<String> getIncludedHeaders() {
        return includedHeaders;
    }

    public Set<String> getExcludedHeaders() {
        return excludedHeaders;
    }

    @Override
    public String toString() {
        return "ResponseProperties{" +
                "shouldCache4xx=" + shouldCache4xx +
                ", shouldCache5xx=" + shouldCache5xx +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }
}
