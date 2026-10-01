package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the {@code idempify.response.*} properties - which answers of a completed operation are kept on its
 * record, and which of their headers survive.
 * <p>
 * Every property is answered: an absent one falls back to its {@code @DefaultValue}, and an absent
 * {@code release-for} - whose default is a list of types an annotation cannot spell - to
 * {@link IdempifyDefaults#DEFAULT_RELEASE_FOR_LIST}, so this block hands the core a fully decided response policy
 * rather than a partial one for a call site to finish.
 * <p>
 * For the two header sets, naming nothing and naming an empty set say the same thing: neither narrows
 * anything, so both leave the response with the headers it carries.
 */
public final class ResponseProperties {

    private final List<Class<? extends Throwable>> releaseFor;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    public ResponseProperties(List<Class<? extends Throwable>> releaseFor,
                              @DefaultValue Set<String> includedHeaders,
                              @DefaultValue Set<String> excludedHeaders) {
        this.releaseFor = releaseFor == null ? IdempifyDefaults.DEFAULT_RELEASE_FOR_LIST : rejectNonThrowables(releaseFor);
        this.includedHeaders = Objects.requireNonNull(includedHeaders, "includedHeaders cannot be null");
        this.excludedHeaders = Objects.requireNonNull(excludedHeaders, "excludedHeaders cannot be null");
    }

    /**
     * The element type is erased by the time the list is bound, so a class that is not an exception gets this
     * far and is refused here, at startup, rather than never matching a failure.
     */
    private static List<Class<? extends Throwable>> rejectNonThrowables(List<Class<? extends Throwable>> releaseFor) {
        for (Class<?> type : releaseFor) {
            if (type == null || !Throwable.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(
                        "'idempify.response.release-for' must name exception types only, but named %s"
                                .formatted(type == null ? null : type.getName())
                );
            }
        }
        return releaseFor;
    }

    public ResponseConfig toResponseConfig() {
        return ResponseConfig.builder()
                .releaseFor(releaseFor)
                .includedHeaders(includedHeaders)
                .excludedHeaders(excludedHeaders)
                .build();
    }

    public List<Class<? extends Throwable>> getReleaseFor() {
        return releaseFor;
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
                "releaseFor=" + releaseFor +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }
}
