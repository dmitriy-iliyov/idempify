package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.conflict.IdempotencyConflictException;
import io.github.dmitriyiliyov.idempify.core.conflict.WaitAbortedException;
import io.github.dmitriyiliyov.idempify.core.conflict.WaitAttemptsExhaustedException;
import io.github.dmitriyiliyov.idempify.core.conflict.WaitTimeoutException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;

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

    public static final List<Class<? extends Throwable>> DEFAULT_RELEASE_FOR_LIST = List.of(
            IdempotentProcessingException.class,
            IdempotencyKeyException.class,
            OperationStatusMismatchException.class,
            SerializationProcessingException.class,
            SerializationException.class,
            DeserializationException.class,
            EmptyRequestBodyException.class,
            InvalidFingerprintException.class,
            FingerprintMismatchException.class,
            IdempotencyConflictException.class,
            OperationDisappearedException.class,
            WaitAbortedException.class,
            WaitAttemptsExhaustedException.class,
            WaitTimeoutException.class
    );

    public static final String HEADER_NAME = "Idempotency-Key";

    public static final String TTL_VALUE = "PT24H";

    public static final String LOCK_TTL_VALUE = "PT5M";

    public static final String PROCESSOR_TYPE_NAME = "NON_BLOCKING";

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
    public static final String CACHE_TYPE_VALUE = "IN_MEMORY";
    public static final String IN_MEMORY_CACHE_CAPACITY_VALUE = "100";

    public static final String METRICS_ENABLED_VALUE = "false";

    private IdempifyDefaults() {}
}
