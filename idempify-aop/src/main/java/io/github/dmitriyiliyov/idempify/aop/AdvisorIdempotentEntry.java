package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.config.IdempotentEntry;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reads the answer off the proxy rather than off the configuration: a call reaches {@link IdempotentAdvisor}
 * only when the bean is a proxy carrying that advisor, the pointcut matches the method, and the proxy is of a
 * kind that can intercept it at all.
 * <p>
 * That the advisor bean exists proves none of the three. It is an aspect, so weaving it is the job of an
 * auto-proxy creator this module does not register - a context started with {@code spring.aop.auto=false}
 * holds the bean and weaves nothing - and what stands in the list of a proxy is then not the bean itself but
 * the advisor Spring generated from it, recognised here by the class declaring its advice method.
 */
public class AdvisorIdempotentEntry implements IdempotentEntry {

    @Override
    public boolean intercepts(Object bean, Method method) {
        if (method == null || !(bean instanceof Advised advised)) {
            return false;
        }

        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        return advisorApplies(advised, specificMethod, targetClass) && canProxyIntercept(advised, bean, specificMethod);
    }

    private boolean advisorApplies(Advised advised, Method method, Class<?> targetClass) {
        for (Advisor advisor : advised.getAdvisors()) {
            if (isIdempotentAdvisor(advisor) && pointcutMatches(advisor, method, targetClass)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdempotentAdvisor(Advisor advisor) {
        return advisor.getAdvice() instanceof AbstractAspectJAdvice advice
                && IdempotentAdvisor.class.isAssignableFrom(advice.getAspectJAdviceMethod().getDeclaringClass());
    }

    private boolean pointcutMatches(Advisor advisor, Method method, Class<?> targetClass) {
        if (!(advisor instanceof PointcutAdvisor pointcutAdvisor)) {
            return false;
        }
        Pointcut pointcut = pointcutAdvisor.getPointcut();
        return pointcut.getClassFilter().matches(targetClass)
                && pointcut.getMethodMatcher().matches(method, targetClass);
    }

    private boolean canProxyIntercept(Advised advised, Object bean, Method method) {
        if (AopUtils.isJdkDynamicProxy(bean)) {
            return declaredOnProxiedInterface(advised, method);
        }
        return !Modifier.isFinal(method.getModifiers());
    }

    private boolean declaredOnProxiedInterface(Advised advised, Method method) {
        for (Class<?> proxiedInterface : advised.getProxiedInterfaces()) {
            if (ClassUtils.hasMethod(proxiedInterface, method)) {
                return true;
            }
        }
        return false;
    }
}
