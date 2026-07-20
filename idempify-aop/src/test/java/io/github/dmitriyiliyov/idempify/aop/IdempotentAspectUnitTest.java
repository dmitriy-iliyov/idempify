package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectUnitTest {

    @Mock
    IdempotentInterceptor interceptor;

    @Mock
    RequestContextProvider requestContextProvider;

    @InjectMocks
    IdempotentAspect tested;

    @Test
    @DisplayName("UT constructor when interceptor is null should throw NullPointerException")
    void constructor_whenInterceptorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(null, requestContextProvider))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("interceptor cannot be null");
    }

    @Test
    @DisplayName("UT constructor when requestContextProvider is null should throw NullPointerException")
    void constructor_whenRequestContextProviderIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new IdempotentAspect(interceptor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestContextProvider cannot be null");
    }

    @Test
    @DisplayName("UT advice() when key is blank should build context with null idempotencyKey and delegate to interceptor")
    void advice_whenKeyIsBlank_shouldBuildContextWithNullIdempotencyKeyAndDelegateToInterceptor() throws Throwable {
        // given
        Idempotent annotation = TestTarget.class.getMethod("action", String.class).getAnnotation(Idempotent.class);
        RequestContext requestContext = mock(RequestContext.class);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        String proceedResult = "proceed-result";
        String interceptResult = "intercept-result";

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getReturnType()).thenReturn(String.class);
        when(requestContextProvider.getContext()).thenReturn(requestContext);
        when(interceptor.intercept(any())).thenReturn(interceptResult);
        when(jp.proceed()).thenReturn(proceedResult);

        ArgumentCaptor<InterceptContext> captor = ArgumentCaptor.forClass(InterceptContext.class);

        // when
        Object result = tested.advice(jp, annotation);

        // then
        assertThat(result).isEqualTo(interceptResult);

        verify(interceptor, times(1)).intercept(captor.capture());
        InterceptContext<?> context = captor.getValue();
        assertThat(context.getOperationResultType()).isEqualTo(String.class);
        assertThat(context.getRequestContext()).isEqualTo(requestContext);
        assertThat(context.getIdempotencyKey()).isNull();
        assertThat(context.getHeaderName()).isEqualTo(annotation.headerName());
        assertThat(context.getTtl()).isEqualTo(annotation.ttl());
        assertThat(context.getTimeUnit()).isEqualTo(annotation.timeUnit());
        assertThat(context.getConflictHandleStrategy()).isEqualTo(annotation.onConflict());
        assertThat(context.getConflictHandlerClass()).isEqualTo(annotation.conflictHandler());
        assertThat(context.useFingerprint()).isEqualTo(annotation.useFingerprint());
        assertThat(context.getFingerprintPolicyClass()).isEqualTo(annotation.fingerprintPolicy());

        assertThat(context.getOperation().call()).isEqualTo(proceedResult);
        verify(jp, times(1)).proceed();
    }

    @Test
    @DisplayName("UT advice() when key is a valid SpEL expression should parse it into the idempotency key")
    void advice_whenKeyIsValidSpelExpression_shouldParseItIntoIdempotencyKey() throws Throwable {
        // given
        UUID key = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Idempotent annotation = TestTarget.class.getMethod("actionWithKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithKey", String.class);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getReturnType()).thenReturn(String.class);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{"value"});
        when(requestContextProvider.getContext()).thenReturn(mock(RequestContext.class));
        when(interceptor.intercept(any())).thenReturn(null);

        ArgumentCaptor<InterceptContext> captor = ArgumentCaptor.forClass(InterceptContext.class);

        // when
        tested.advice(jp, annotation);

        // then
        verify(interceptor, times(1)).intercept(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("UT resolveReturnType() should return the intercepted method's return type")
    void resolveReturnType_shouldReturnInterceptedMethodReturnType() {
        // given
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getReturnType()).thenReturn(String.class);

        // when
        Class<Object> result = tested.resolveReturnType(jp);

        // then
        assertThat(result).isEqualTo(String.class);
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when key is blank should return null")
    void parseIdempotencyKey_whenKeyIsBlank_shouldReturnNull() throws NoSuchMethodException {
        // given
        Idempotent annotation = TestTarget.class.getMethod("action", String.class).getAnnotation(Idempotent.class);
        JoinPoint jp = mock(JoinPoint.class);

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(jp);
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression evaluates to valid UUID string should return parsed UUID")
    void parseIdempotencyKey_whenSpelEvaluatesToValidUuidString_shouldReturnParsedUuid() throws NoSuchMethodException {
        // given
        UUID key = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Idempotent annotation = TestTarget.class.getMethod("actionWithKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithKey", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{"value"});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isEqualTo(key);
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression evaluates to null should return null")
    void parseIdempotencyKey_whenSpelEvaluatesToNull_shouldReturnNull() throws NoSuchMethodException {
        // given
        Idempotent annotation = TestTarget.class.getMethod("actionWithNullKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithNullKey", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{"value"});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression evaluates to invalid UUID format should return null")
    void parseIdempotencyKey_whenSpelEvaluatesToInvalidUuidFormat_shouldReturnNull() throws NoSuchMethodException {
        // given
        Idempotent annotation = TestTarget.class.getMethod("actionWithInvalidKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithInvalidKey", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{"value"});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression cannot be evaluated should return null")
    void parseIdempotencyKey_whenSpelExpressionCannotBeEvaluated_shouldReturnNull() throws NoSuchMethodException {
        // given
        Idempotent annotation = TestTarget.class.getMethod("actionWithUnresolvableKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithUnresolvableKey", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{"value"});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression references a method argument by index should resolve it")
    void parseIdempotencyKey_whenSpelExpressionReferencesMethodArgumentByIndex_shouldResolveIt() throws NoSuchMethodException {
        // given
        UUID key = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Idempotent annotation = TestTarget.class.getMethod("actionWithA0Key", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithA0Key", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{key.toString()});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isEqualTo(key);
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression references a bare method parameter by name should resolve it")
    void parseIdempotencyKey_whenSpelExpressionReferencesBareMethodParameterByName_shouldResolveIt() throws NoSuchMethodException {
        // given
        UUID key = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Idempotent annotation = TestTarget.class.getMethod("actionWithParamNameKey", String.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithParamNameKey", String.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{key.toString()});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isEqualTo(key);
    }

    @Test
    @DisplayName("UT parseIdempotencyKey() when SpEL expression references a property of a method argument should resolve it")
    void parseIdempotencyKey_whenSpelExpressionReferencesPropertyOfMethodArgument_shouldResolveIt() throws NoSuchMethodException {
        // given
        UUID key = UUID.fromString("33333333-3333-3333-3333-333333333333");
        TestRequest request = new TestRequest(key.toString());
        Idempotent annotation = TestTarget.class.getMethod("actionWithRequestIdKey", TestRequest.class).getAnnotation(Idempotent.class);
        Method method = TestTarget.class.getMethod("actionWithRequestIdKey", TestRequest.class);
        JoinPoint jp = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);

        when(jp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(jp.getTarget()).thenReturn(new TestTarget());
        when(jp.getArgs()).thenReturn(new Object[]{request});

        // when
        UUID result = tested.parseIdempotencyKey(jp, annotation);

        // then
        assertThat(result).isEqualTo(key);
    }

    private static class TestTarget {

        @Idempotent
        public String action(String value) {
            return value;
        }

        @Idempotent(key = "'11111111-1111-1111-1111-111111111111'")
        public String actionWithKey(String value) {
            return value;
        }

        @Idempotent(key = "null")
        public String actionWithNullKey(String value) {
            return value;
        }

        @Idempotent(key = "'not-a-uuid'")
        public String actionWithInvalidKey(String value) {
            return value;
        }

        @Idempotent(key = "nonExistentProperty")
        public String actionWithUnresolvableKey(String value) {
            return value;
        }

        @Idempotent(key = "#idempotencyKey")
        public String actionWithParamNameKey(String idempotencyKey) {
            return idempotencyKey;
        }

        @Idempotent(key = "#a0")
        public String actionWithA0Key(String value) {
            return value;
        }

        @Idempotent(key = "#request.id")
        public String actionWithRequestIdKey(TestRequest request) {
            return request.getId();
        }
    }

    private static class TestRequest {
        private final String id;

        TestRequest(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
