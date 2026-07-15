package io.github.dmitriyiliyov.springidempotency.aop;

import io.github.dmitriyiliyov.springidempotency.core.IdempotencyConstants;
import io.github.dmitriyiliyov.springidempotency.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.springidempotency.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.springidempotency.core.conflict.RejectConflictHandler;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.DefaultFingerprintPolicy;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintPolicy;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Idempotent {

    String key() default "";

    @AliasFor("metadataKey")
    String headerName() default IdempotencyConstants.HEADER_NAME;

    @AliasFor("headerName")
    String metadataKey() default IdempotencyConstants.HEADER_NAME;

    long ttl() default 24;

    TimeUnit timeUnit() default TimeUnit.HOURS;

    ConflictHandleStrategy onConflict() default ConflictHandleStrategy.REJECT;

    Class<? extends ConflictHandler> conflictHandler() default RejectConflictHandler.class;

    boolean useFingerprint() default true;

    Class<? extends FingerprintPolicy> fingerprintPolicy() default DefaultFingerprintPolicy.class;
}
