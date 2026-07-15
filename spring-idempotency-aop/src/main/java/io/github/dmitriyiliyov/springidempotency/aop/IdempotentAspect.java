package io.github.dmitriyiliyov.springidempotency.aop;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Objects;

@Aspect
public class IdempotentAspect {

    private final IdempotentInterceptor interceptor;

    public IdempotentAspect(IdempotentInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor cannot be null");
    }

    @Pointcut("@annotation(idempotent) && execution(public * *(..))")
    public void pointcut(Idempotent idempotent) { }

    @Around()
    public void advice() {

    }
}
