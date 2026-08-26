package io.github.dmitriyiliyov.idempify.core;

import java.time.Duration;

/**
 * The answers the library gives when no configuration source decides a setting.
 * <p>
 * Each default is written once as a {@code String}, because that is the only form an annotation argument can
 * take - the properties module states its defaults in {@code @DefaultValue}, where neither a {@link Duration}
 * nor an enum constant can be written. A setting whose typed value is needed as well derives it from that
 * same string, so the two cannot say different things.
 * <p>
 * The config classes read these strings for their own {@code DEFAULT_*} constants, so a default is written
 * here once and answered the same way from both sides; that the two agree is what
 * {@code PropertyDefaultsUnitTest} checks. Response caching is the one answer that is a plain "no": it stays
 * off until an application names a cache, since entries of different applications sharing a store must not be
 * merged under a name nobody chose.
 */
public final class IdempifyDefaults {

    public static final String ENABLED_VALUE = "true";

    public static final String HEADER_NAME = "Idempotency-Key";

    public static final String TTL_VALUE = "PT24H";

    public static final String PROCESSOR_TYPE_NAME = "LOCK_BASED";

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

    public static final String CACHE_ENABLED_VALUE = "false";
    public static final String CACHE_4XX_VALUE = "false";
    public static final String CACHE_5XX_VALUE = "false";
    public static final String IN_MEMORY_CACHE_CAPACITY_VALUE = "100";

    private IdempifyDefaults() {}
}
