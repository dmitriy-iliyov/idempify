package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;

import java.time.Duration;
import java.util.List;

/**
 * The answers the library gives when no configuration source decides a setting.
 * <p>
 * Each default is written once as a {@code String}, because that is the only form an annotation argument can
 * take - the properties module states its defaults in {@code @DefaultValue}, where neither a {@link Duration}
 * nor an enum constant can be written. A setting whose typed value is needed as well derives it from that
 * same string, so the two cannot say different things.
 * <p>
 * The config classes read these strings for their own {@code DEFAULT_*} constants, so a default is written
 * here once and answered the same way from both sides. One of the answers is a plain "no": no cache stands in
 * front of the store until it is asked for.
 */
public final class IdempifyDefaults {

    public static final String ENABLED_VALUE = "true";

    public static final String DEFAULT_CONFIG_BEAN_NAME = "idempifyDefaultIdempotencyConfig";

    /**
     * Names of the two dispatchers the core declares {@code @Primary}. Every transport and every processor type is a
     * bean of the type the dispatcher answers to, so a replacement cannot be found by type: an application
     * displaces the dispatcher by declaring a bean under its name.
     */
    public static final String DELEGATING_KEY_EXTRACTOR_BEAN_NAME = "idempifyDelegatingKeyExtractor";
    public static final String DELEGATING_PROCESSOR_BEAN_NAME = "idempifyDelegatingIdempotentProcessor";

    public static final String HEADER_NAME = "Idempotency-Key";

    public static final String TTL_VALUE = "PT24H";

    public static final String LOCK_TTL_VALUE = "PT5M";

    public static final String PROCESSOR_TYPE_NAME = "TRANSACTIONAL_NON_BLOCKING";

    public static final String CONFLICT_ENABLED_VALUE = "true";
    public static final String CONFLICT_HANDLE_STRATEGY_VALUE = "REJECT";

    public static final String WAIT_DELAY_VALUE = "PT5S";
    public static final String WAIT_MULTIPLIER_VALUE = "1.5";
    public static final String WAIT_MAX_ATTEMPTS_VALUE = "5";
    public static final String WAIT_MAX_DURATION_VALUE = "PT60S";

    public static final String FINGERPRINT_ENABLED_VALUE = "true";
    public static final String BODY_HANDLE_STRATEGY_VALUE = "CANONICALIZED_BODY_HASH";
    public static final String EMPTY_BODY_FALLBACK_VALUE = "THROWING";
    public static final String BODY_FORMAT_VALUE = "JSON";
    public static final String CANONICALIZE_STRATEGY_VALUE = "LEXICOGRAPHICAL";

    /**
     * The built-in {@code recordFor} of {@link ProcessorType#EXTERNAL_NON_BLOCKING}: its effect is not rolled back,
     * so every failure is kept rather than risk running the operation a second time.
     */
    public static final List<Class<? extends Throwable>> EXTERNAL_NON_BLOCKING_RECORD_FOR_LIST =
            List.of(Throwable.class);

    /**
     * The built-in {@code recordFor} of {@link ProcessorType#TRANSACTIONAL_NON_BLOCKING}: the failure rolls the effect
     * back, so every failure releases the key.
     */
    public static final List<Class<? extends Throwable>> TRANSACTIONAL_NON_BLOCKING_RECORD_FOR_LIST = List.of();

    /**
     * The built-in {@code recordFor} of {@link ProcessorType#TRANSACTIONAL_BLOCKING}, the only one it accepts: a
     * rollback takes the record away with the failure.
     */
    public static final List<Class<? extends Throwable>> TRANSACTIONAL_BLOCKING_RECORD_FOR_LIST = List.of();

    /**
     * The built-in {@code recordFor} of a processor. It is asked for once, after the last layer has spoken: the
     * answer depends on the processor that is finally chosen, which no layer below the last one can know.
     */
    public static List<Class<? extends Throwable>> recordForOf(ProcessorType processorType) {
        return switch (processorType) {
            case TRANSACTIONAL_BLOCKING -> TRANSACTIONAL_BLOCKING_RECORD_FOR_LIST;
            case TRANSACTIONAL_NON_BLOCKING -> TRANSACTIONAL_NON_BLOCKING_RECORD_FOR_LIST;
            case EXTERNAL_NON_BLOCKING -> EXTERNAL_NON_BLOCKING_RECORD_FOR_LIST;
        };
    }

    public static final String CACHE_ENABLED_VALUE = "false";
    public static final String CACHE_TYPE_VALUE = "IN_MEMORY";
    public static final String IN_MEMORY_CACHE_CAPACITY_VALUE = "100";

    public static final String METRICS_ENABLED_VALUE = "false";

    private IdempifyDefaults() {}
}
