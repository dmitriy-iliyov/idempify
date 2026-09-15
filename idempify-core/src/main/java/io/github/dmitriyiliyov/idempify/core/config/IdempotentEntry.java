package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.Idempotent;

import java.lang.reflect.Method;

/**
 * An entry into the library: what turns a call to an annotated method into an operation.
 * <p>
 * A transport collects the call sites it answers for by reading {@link Idempotent} off them, while an entry
 * reaches them by a rule of its own - a pointcut, a proxy, a handler chain - and the two sets need not agree.
 * A call site the transport counts as idempotent and no entry intercepts has no protection at all, and
 * nothing about it looks wrong from outside: the endpoint answers, the key is accepted, and every request
 * runs. Asking the entry turns that into an answer available at start-up rather than into a duplicate in
 * production.
 */
public interface IdempotentEntry {

    /**
     * Answers whether a call to {@code method} on {@code bean} would reach this entry, as the context stands
     * rather than as the entry was configured: an entry that was never woven, a bean that was never proxied
     * and a method the entry does not match all answer {@code false}.
     * <p>
     * Callable once the singletons are in place - asked earlier, it answers about beans that are not finished.
     *
     * @param bean   the object a call is dispatched to, proxies and all, rather than the target behind them.
     * @param method the method as the caller declares it.
     */
    boolean intercepts(Object bean, Method method);
}
