package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectUnitTest {

    @Mock
    IdempotentOperationExpressionEvaluator expressionEvaluator;

    @Mock
    RequestContextProvider requestContextProvider;

    @Mock
    OperationMetadataFactory operationMetadataFactory;

    @Mock
    IdempotentInterceptor interceptor;

    @Mock
    ProceedingJoinPoint jp;

    @Mock
    MethodSignature signature;

    @Mock
    RequestContext requestContext;

    @InjectMocks
    IdempotentAspect tested;

    OperationMetadata operationMetadata = TestOperationMetadata.builder().build();

    @Test
    @DisplayName("UT constructor when expressionEvaluator is null should throw NullPointerException")
    void constructor_whenExpressionEvaluatorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(null, requestContextProvider, operationMetadataFactory, interceptor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expressionEvaluator cannot be null");
    }

    @Test
    @DisplayName("UT constructor when requestContextProvider is null should throw NullPointerException")
    void constructor_whenRequestContextProviderIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(expressionEvaluator, null, operationMetadataFactory, interceptor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestContextProvider cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationMetadataFactory is null should throw NullPointerException")
    void constructor_whenOperationMetadataFactoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(expressionEvaluator, requestContextProvider, null, interceptor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationMetadataFactory cannot be null");
    }

    @Test
    @DisplayName("UT constructor when interceptor is null should throw NullPointerException")
    void constructor_whenInterceptorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(expressionEvaluator, requestContextProvider, operationMetadataFactory, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("interceptor cannot be null");
    }

    @Test
    @DisplayName("UT pointcut() when called should do nothing on its own")
    void pointcut_whenCalled_shouldDoNothingOnItsOwn() throws NoSuchMethodException {
        // when
        tested.pointcut(annotation("action"));

        // then
        verifyNoInteractions(expressionEvaluator, requestContextProvider, operationMetadataFactory, interceptor);
    }

    @Test
    @DisplayName("UT advice() when interceptor returns a result should return it to the caller")
    void advice_whenInterceptorReturnsResult_shouldReturnItToCaller() throws Throwable {
        // given
        String interceptResult = "intercept-result";
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);
        when(interceptor.intercept(any())).thenReturn(interceptResult);

        // when
        Object result = tested.advice(jp, annotation);

        // then
        assertThat(result).isEqualTo(interceptResult);
    }

    @Test
    @DisplayName("UT advice() when called should build the context from the join point")
    void advice_whenCalled_shouldBuildContextFromJoinPoint() throws Throwable {
        // given
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);

        // when
        tested.advice(jp, annotation);

        // then
        InterceptContext<?> context = capturedContext();
        assertThat(context.getOperationResultType()).isEqualTo(String.class);
        assertThat(context.getRequestContext()).isSameAs(requestContext);
        assertThat(context.getOperationMetadata()).isSameAs(operationMetadata);
    }

    @Test
    @DisplayName("UT advice() when the built callback is called should proceed the join point")
    void advice_whenBuiltCallbackIsCalled_shouldProceedJoinPoint() throws Throwable {
        // given
        String proceedResult = "proceed-result";
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);
        when(jp.proceed()).thenReturn(proceedResult);

        // when
        tested.advice(jp, annotation);

        // then
        verify(jp, never()).proceed();
        assertThat(capturedContext().getOperationCallback().call()).isEqualTo(proceedResult);
        verify(jp, times(1)).proceed();
    }

    @Test
    @DisplayName("UT advice() when the intercepted method throws should let the exception through the callback")
    void advice_whenInterceptedMethodThrows_shouldLetExceptionThroughCallback() throws Throwable {
        // given
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);
        when(jp.proceed()).thenThrow(new IllegalStateException("boom"));

        // when
        tested.advice(jp, annotation);

        // then
        assertThatThrownBy(() -> capturedContext().getOperationCallback().call())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    @DisplayName("UT advice() when the annotation names no key should not touch the evaluator")
    void advice_whenAnnotationNamesNoKey_shouldNotTouchEvaluator() throws Throwable {
        // given
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);

        // when
        tested.advice(jp, annotation);

        // then
        assertThat(capturedContext().getIdempotencyKey()).isNull();
        verifyNoInteractions(expressionEvaluator);
    }

    @Test
    @DisplayName("UT advice() when the annotation names a key should hand the call to the evaluator")
    void advice_whenAnnotationNamesKey_shouldHandCallToEvaluator() throws Throwable {
        // given
        UUID key = UUID.randomUUID();
        Idempotent annotation = annotation("actionWithKey");
        Method method = method("actionWithKey");
        Object[] args = new Object[]{"value"};
        TestTarget target = new TestTarget();

        stubAdvice(annotation);
        stubJoinPointCall(method, target, args);
        when(expressionEvaluator.evaluateIdempotencyKey(annotation.idempotencyKey(), method, TestTarget.class, target, args))
                .thenReturn(key);

        // when
        tested.advice(jp, annotation);

        // then
        verify(expressionEvaluator, times(1))
                .evaluateIdempotencyKey(annotation.idempotencyKey(), method, TestTarget.class, target, args);
        assertThat(capturedContext().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("UT advice() when the join point has no target should pass a null target class to the evaluator")
    void advice_whenJoinPointHasNoTarget_shouldPassNullTargetClassToEvaluator() throws Throwable {
        // given
        UUID key = UUID.randomUUID();
        Idempotent annotation = annotation("actionWithKey");
        Method method = method("actionWithKey");
        Object[] args = new Object[]{"value"};

        stubAdvice(annotation);
        stubJoinPointCall(method, null, args);
        when(expressionEvaluator.evaluateIdempotencyKey(annotation.idempotencyKey(), method, null, null, args))
                .thenReturn(key);

        // when
        tested.advice(jp, annotation);

        // then
        assertThat(capturedContext().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("UT advice() when the evaluator yields the text of a UUID should parse it into the idempotencyKey")
    void advice_whenEvaluatorYieldsTextOfUuid_shouldParseItIntoIdempotencyKey() throws Throwable {
        // given
        UUID key = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Idempotent annotation = annotation("actionWithKey");

        stubAdvice(annotation);
        stubJoinPointCall(method("actionWithKey"), new TestTarget(), new Object[]{"value"});
        when(expressionEvaluator.evaluateIdempotencyKey(any(), any(), any(), any(), any())).thenReturn(key.toString());

        // when
        tested.advice(jp, annotation);

        // then
        assertThat(capturedContext().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("UT advice() when the evaluator yields a UUID should take it as the idempotencyKey")
    void advice_whenEvaluatorYieldsUuid_shouldTakeItAsIdempotencyKey() throws Throwable {
        // given
        UUID key = UUID.randomUUID();
        Idempotent annotation = annotation("actionWithKey");

        stubAdvice(annotation);
        stubJoinPointCall(method("actionWithKey"), new TestTarget(), new Object[]{"value"});
        when(expressionEvaluator.evaluateIdempotencyKey(any(), any(), any(), any(), any())).thenReturn(key);

        // when
        tested.advice(jp, annotation);

        // then
        assertThat(capturedContext().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("UT advice() when the evaluator yields null should throw IllegalArgumentException")
    void advice_whenEvaluatorYieldsNull_shouldThrowIllegalArgumentException() throws NoSuchMethodException {
        // given
        Idempotent annotation = annotation("actionWithKey");

        stubJoinPointSignature();
        stubJoinPointCall(method("actionWithKey"), new TestTarget(), new Object[]{"value"});
        when(expressionEvaluator.evaluateIdempotencyKey(any(), any(), any(), any(), any())).thenReturn(null);

        // when // then
        assertThatThrownBy(() -> tested.advice(jp, annotation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluated to null");
        verifyNoInteractions(operationMetadataFactory, requestContextProvider, interceptor);
    }

    @Test
    @DisplayName("UT advice() when the evaluator yields neither a UUID nor its text should throw IllegalArgumentException")
    void advice_whenEvaluatorYieldsNeitherUuidNorItsText_shouldThrowIllegalArgumentException() throws NoSuchMethodException {
        // given
        Idempotent annotation = annotation("actionWithKey");

        stubJoinPointSignature();
        stubJoinPointCall(method("actionWithKey"), new TestTarget(), new Object[]{"value"});
        when(expressionEvaluator.evaluateIdempotencyKey(any(), any(), any(), any(), any())).thenReturn(10);

        // when // then
        assertThatThrownBy(() -> tested.advice(jp, annotation))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operationMetadataFactory, requestContextProvider, interceptor);
    }

    @Test
    @DisplayName("UT advice() when the evaluator cannot evaluate the expression should let the exception through")
    void advice_whenEvaluatorCannotEvaluateExpression_shouldLetExceptionThrough() throws NoSuchMethodException {
        // given
        Idempotent annotation = annotation("actionWithKey");

        stubJoinPointSignature();
        stubJoinPointCall(method("actionWithKey"), new TestTarget(), new Object[]{"value"});
        when(expressionEvaluator.evaluateIdempotencyKey(any(), any(), any(), any(), any()))
                .thenThrow(new SpelEvaluationException(SpelMessage.PROPERTY_OR_FIELD_NOT_READABLE, "nope", "TestTarget"));

        // when // then
        assertThatThrownBy(() -> tested.advice(jp, annotation))
                .isInstanceOf(EvaluationException.class);
        verifyNoInteractions(operationMetadataFactory, requestContextProvider, interceptor);
    }

    @Test
    @DisplayName("UT advice() when the interceptor throws should let the exception through")
    void advice_whenInterceptorThrows_shouldLetExceptionThrough() throws NoSuchMethodException {
        // given
        Idempotent annotation = annotation("action");

        stubAdvice(annotation);
        when(interceptor.intercept(any())).thenThrow(new IllegalStateException("boom"));

        // when // then
        assertThatThrownBy(() -> tested.advice(jp, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    private void stubJoinPointSignature() {
        when(jp.getSignature()).thenReturn(signature);
    }

    private void stubAdvice(Idempotent annotation) {
        stubJoinPointSignature();
        when(signature.getReturnType()).thenReturn(String.class);
        when(operationMetadataFactory.generate(annotation, jp)).thenReturn(operationMetadata);
        when(requestContextProvider.getContext()).thenReturn(requestContext);
    }

    private void stubJoinPointCall(Method method, Object target, Object[] args) {
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(target);
        when(jp.getArgs()).thenReturn(args);
    }

    private InterceptContext<?> capturedContext() {
        ArgumentCaptor<InterceptContext<?>> captor = interceptContextCaptor();
        verify(interceptor, times(1)).intercept(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<InterceptContext<?>> interceptContextCaptor() {
        return ArgumentCaptor.forClass(InterceptContext.class);
    }

    private Method method(String name) throws NoSuchMethodException {
        return TestTarget.class.getMethod(name, String.class);
    }

    private Idempotent annotation(String methodName) throws NoSuchMethodException {
        return method(methodName).getAnnotation(Idempotent.class);
    }

    public static class TestTarget {

        @Idempotent
        public String action(String value) {
            return value;
        }

        @Idempotent(idempotencyKey = "#a0")
        public String actionWithKey(String value) {
            return value;
        }
    }
}
