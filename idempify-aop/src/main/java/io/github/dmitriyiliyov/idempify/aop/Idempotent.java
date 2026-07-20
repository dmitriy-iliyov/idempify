package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotencyConstants;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.conflict.RejectConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method as idempotent, ensuring that it is executed only once for a given idempotency key.
 * <p>
 * This annotation triggers an aspect that intercepts the method call, checks for a unique idempotency key,
 * and stores the result. Subsequent calls with the same key will return the stored result without
 * re-executing the method, until the TTL expires.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Idempotent {

    String key() default "";

    String headerName() default IdempotencyConstants.HEADER_NAME;

    long ttl() default 24;

    TimeUnit timeUnit() default TimeUnit.HOURS;

    ConflictHandleStrategy onConflict() default ConflictHandleStrategy.REJECT;

    Class<? extends ConflictHandler> conflictHandler() default RejectConflictHandler.class;

    boolean useFingerprint() default true;

    Class<? extends FingerprintPolicy> fingerprintPolicy() default DefaultFingerprintPolicy.class;
}
