package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Everything here is asked of a real proxy: what the class answers is a property of how the bean was
 * assembled, and a mocked proxy would only repeat the assumption under test.
 */
class AdvisorIdempotentEntryUnitTest {

    private final AdvisorIdempotentEntry tested = new AdvisorIdempotentEntry();

    @Test
    @DisplayName("UT intercepts() when the bean is no proxy at all should answer false")
    void intercepts_whenBeanIsNoProxyAtAll_shouldAnswerFalse() {
        // given
        TestController bean = new TestController();

        // when
        boolean result = tested.intercepts(bean, method("pay"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when the method is null should answer false")
    void intercepts_whenMethodIsNull_shouldAnswerFalse() {
        // when
        boolean result = tested.intercepts(advisedProxy(new TestController()), null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when the proxy carries the advisor should answer true")
    void intercepts_whenProxyCarriesAdvisor_shouldAnswerTrue() {
        // when
        boolean result = tested.intercepts(advisedProxy(new TestController()), method("pay"));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("UT intercepts() when the proxy carries somebody else advice only should answer false")
    void intercepts_whenProxyCarriesSomebodyElseAdviceOnly_shouldAnswerFalse() {
        // given
        ProxyFactory factory = new ProxyFactory(new TestController());
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());

        // when
        boolean result = tested.intercepts(factory.getProxy(), method("pay"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when the method carries no annotation should answer false")
    void intercepts_whenMethodCarriesNoAnnotation_shouldAnswerFalse() {
        // when
        boolean result = tested.intercepts(advisedProxy(new TestController()), method("refund"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when the annotated method is not public should answer false")
    void intercepts_whenAnnotatedMethodIsNotPublic_shouldAnswerFalse() {
        // when
        boolean result = tested.intercepts(advisedProxy(new TestController()), declaredMethod("hiddenPay"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when the annotated method is final under a class proxy should answer false")
    void intercepts_whenAnnotatedMethodIsFinalUnderClassProxy_shouldAnswerFalse() {
        // when
        boolean result = tested.intercepts(advisedProxy(new TestController()), method("finalPay"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when a jdk proxy hides the annotated method should answer false")
    void intercepts_whenJdkProxyHidesAnnotatedMethod_shouldAnswerFalse() {
        // given
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestService());
        factory.setProxyTargetClass(false);
        factory.addAspect(advisor());

        // when
        boolean result = tested.intercepts(factory.getProxy(), methodOf(TestService.class, "payOffInterface"));

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("UT intercepts() when a jdk proxy exposes the annotated method on its interface should answer true")
    void intercepts_whenJdkProxyExposesAnnotatedMethodOnItsInterface_shouldAnswerTrue() {
        // given
        AspectJProxyFactory factory = new AspectJProxyFactory(new AnnotatedService());
        factory.setProxyTargetClass(false);
        factory.addAspect(advisor());

        // when
        boolean result = tested.intercepts(factory.getProxy(), methodOf(Payments.class, "pay"));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("UT intercepts() when the proxy carries another aspect's advice only should answer false")
    void intercepts_whenProxyCarriesAnotherAspectsAdviceOnly_shouldAnswerFalse() {
        // given
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestController());
        factory.setProxyTargetClass(true);
        factory.addAspect(new OtherAspect());

        // when
        boolean result = tested.intercepts(factory.getProxy(), method("pay"));

        // then
        assertThat(result).isFalse();
    }

    private static Object advisedProxy(Object target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(advisor());
        return factory.getProxy();
    }

    private static IdempotentAdvisor advisor() {
        return new IdempotentAdvisor(
                new IdempotentOperationExpressionEvaluator(),
                mock(RequestContextProvider.class),
                mock(OperationMetadataResolver.class),
                mock(IdempotentInterceptor.class)
        );
    }

    private static Method method(String name) {
        return methodOf(TestController.class, name);
    }

    private static Method declaredMethod(String name) {
        try {
            return TestController.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException(nsme);
        }
    }

    private static Method methodOf(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException(nsme);
        }
    }

    public static class TestController {

        @Idempotent
        public void pay() { }

        public void refund() { }

        @Idempotent
        public final void finalPay() { }

        @Idempotent
        void hiddenPay() { }
    }

    /**
     * Carries the annotated method on the class only, so a jdk proxy over {@link Payments} cannot reach it.
     */
    public static class TestService implements Payments {

        @Override
        public void pay() { }

        @Idempotent
        public void payOffInterface() { }
    }

    /**
     * Carries the annotated method on the interface a jdk proxy exposes, so the proxy can reach it.
     */
    public static class AnnotatedService implements Payments {

        @Idempotent
        @Override
        public void pay() { }
    }

    /**
     * An aspect advising the same methods as {@link IdempotentAdvisor}, so that only the declaring class of
     * the advice tells the two apart.
     */
    @Aspect
    public static class OtherAspect {

        @Around("@annotation(io.github.dmitriyiliyov.idempify.core.Idempotent)")
        public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
            return joinPoint.proceed();
        }
    }

    public interface Payments {

        void pay();
    }
}
