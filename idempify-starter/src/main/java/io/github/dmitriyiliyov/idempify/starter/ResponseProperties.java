package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.Toggle;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Holds the {@code idempify.response.*} properties - which answers of a completed operation are kept on its
 * record, and which of their headers survive.
 * <p>
 * An absent {@code record-for} stays undecided ({@code null}) rather than getting an answer here: the answer depends on
 * the processor that is finally chosen, which a call site or a named config can still change, so the core gives it
 * once the last layer has spoken. An empty list, by contrast, is a decision - keep no failure. The other
 * properties fall back to their {@code @DefaultValue}.
 * <p>
 * For the two header sets, naming nothing and naming an empty set say the same thing: neither narrows
 * anything, so both leave the response with the headers it carries.
 */
public final class ResponseProperties {

    private final List<Class<? extends Throwable>> recordFor;
    private final Toggle recordForToggle;
    private final Set<String> includedHeaders;
    private final Set<String> excludedHeaders;

    public ResponseProperties(List<Class<? extends Throwable>> recordFor,
                              @DefaultValue Set<String> includedHeaders,
                              @DefaultValue Set<String> excludedHeaders) {
        this.recordFor = recordFor == null ? null : rejectNonThrowables(recordFor);
        this.recordForToggle = recordFor == null ? Toggle.UNSELECTED : Toggle.ENABLE;
        this.includedHeaders = Objects.requireNonNull(includedHeaders, "includedHeaders cannot be null");
        this.excludedHeaders = Objects.requireNonNull(excludedHeaders, "excludedHeaders cannot be null");
    }

    /**
     * The element type is erased by the time the list is bound, so a class that is not an exception gets this
     * far and is refused here, at startup, rather than never matching a failure.
     */
    private static List<Class<? extends Throwable>> rejectNonThrowables(List<Class<? extends Throwable>> recordFor) {
        for (Class<?> type : recordFor) {
            if (type == null || !Throwable.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(
                        "'idempify.response.record-for' must name exception types only, but named %s"
                                .formatted(type == null ? null : type.getName())
                );
            }
        }
        return recordFor;
    }

    public ResponseConfig toResponseConfig() {
        ResponseConfig.Builder builder = ResponseConfig.builder()
                .includedHeaders(includedHeaders)
                .excludedHeaders(excludedHeaders);
        if (recordFor != null) {
            builder.recordFor(recordFor);
        }
        return builder.build();
    }

    public List<Class<? extends Throwable>> getRecordFor() {
        return recordFor;
    }

    public Toggle getRecordForToggle() {
        return recordForToggle;
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
                "recordFor=" + recordFor +
                ", includedHeaders=" + includedHeaders +
                ", excludedHeaders=" + excludedHeaders +
                '}';
    }
}
