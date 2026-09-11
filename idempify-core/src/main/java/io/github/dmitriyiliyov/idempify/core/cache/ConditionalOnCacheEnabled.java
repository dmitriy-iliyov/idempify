package io.github.dmitriyiliyov.idempify.core.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
        prefix = "idempify.cache",
        name = "enabled",
        havingValue = "true"
)
public @interface ConditionalOnCacheEnabled { }
