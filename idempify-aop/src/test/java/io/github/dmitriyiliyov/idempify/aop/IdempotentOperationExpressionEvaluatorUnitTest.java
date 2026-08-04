package io.github.dmitriyiliyov.idempify.aop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class IdempotentOperationExpressionEvaluatorUnitTest {

    IdempotentOperationExpressionEvaluator tested = new IdempotentOperationExpressionEvaluator();

    TestTarget target = new TestTarget();

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression is a literal should return it")
    void evaluateIdempotencyKey_whenExpressionIsLiteral_shouldReturnIt() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object result = evaluate("'11111111-1111-1111-1111-111111111111'", method, "value");

        // then
        assertThat(result).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression references a parameter by name should resolve it")
    void evaluateIdempotencyKey_whenExpressionReferencesParameterByName_shouldResolveIt() throws NoSuchMethodException {
        // given
        Method method = method("actionWithParamNameKey", String.class);

        // when
        Object result = evaluate("#idempotencyKey", method, "resolved");

        // then
        assertThat(result).isEqualTo("resolved");
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression references an argument by index should resolve it")
    void evaluateIdempotencyKey_whenExpressionReferencesArgumentByIndex_shouldResolveIt() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object result = evaluate("#a0", method, "resolved");

        // then
        assertThat(result).isEqualTo("resolved");
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression references a property of an argument should resolve it")
    void evaluateIdempotencyKey_whenExpressionReferencesPropertyOfArgument_shouldResolveIt() throws NoSuchMethodException {
        // given
        UUID uuid = UUID.randomUUID();
        Method method = method("actionWithRequest", TestRequest.class);

        // when
        Object result = evaluate("#request.uuid", method, new TestRequest(uuid));

        // then
        assertThat(result).isEqualTo(uuid);
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression references the target should resolve it against the root object")
    void evaluateIdempotencyKey_whenExpressionReferencesTarget_shouldResolveItAgainstRootObject() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object result = evaluate("name", method, "value");

        // then
        assertThat(result).isEqualTo("test-target");
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression yields null should return null")
    void evaluateIdempotencyKey_whenExpressionYieldsNull_shouldReturnNull() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object result = evaluate("null", method, "value");

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression cannot be evaluated should throw EvaluationException")
    void evaluateIdempotencyKey_whenExpressionCannotBeEvaluated_shouldThrowEvaluationException() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when // then
        assertThatThrownBy(() -> evaluate("nonExistentProperty", method, "value"))
                .isInstanceOf(EvaluationException.class);
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when there is no target should still evaluate expressions over the arguments")
    void evaluateIdempotencyKey_whenThereIsNoTarget_shouldStillEvaluateExpressionsOverArguments() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object result = tested.evaluateIdempotencyKey("#a0", method, null, null, new Object[]{"resolved"});

        // then
        assertThat(result).isEqualTo("resolved");
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the same expression is evaluated twice should parse it only once")
    void evaluateIdempotencyKey_whenSameExpressionIsEvaluatedTwice_shouldParseItOnlyOnce() throws NoSuchMethodException {
        // given
        CountingEvaluator counting = new CountingEvaluator();
        Method method = method("action", String.class);

        // when
        counting.evaluateIdempotencyKey("#a0", method, TestTarget.class, target, new Object[]{"first"});
        counting.evaluateIdempotencyKey("#a0", method, TestTarget.class, target, new Object[]{"second"});

        // then
        assertThat(counting.parserCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when the expression differs should parse each of them")
    void evaluateIdempotencyKey_whenExpressionDiffers_shouldParseEachOfThem() throws NoSuchMethodException {
        // given
        CountingEvaluator counting = new CountingEvaluator();
        Method method = method("action", String.class);

        // when
        counting.evaluateIdempotencyKey("#a0", method, TestTarget.class, target, new Object[]{"value"});
        counting.evaluateIdempotencyKey("name", method, TestTarget.class, target, new Object[]{"value"});

        // then
        assertThat(counting.parserCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("UT evaluateIdempotencyKey() when a cached expression is reused should still see the arguments of the current call")
    void evaluateIdempotencyKey_whenCachedExpressionIsReused_shouldStillSeeArgumentsOfCurrentCall() throws NoSuchMethodException {
        // given
        Method method = method("action", String.class);

        // when
        Object first = evaluate("#a0", method, "first");
        Object second = evaluate("#a0", method, "second");

        // then
        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
    }

    private Object evaluate(String expression, Method method, Object argument) {
        return tested.evaluateIdempotencyKey(expression, method, TestTarget.class, target, new Object[]{argument});
    }

    private Method method(String name, Class<?> parameterType) throws NoSuchMethodException {
        return TestTarget.class.getMethod(name, parameterType);
    }

    private static class CountingEvaluator extends IdempotentOperationExpressionEvaluator {

        int parserCalls;

        @Override
        protected SpelExpressionParser getParser() {
            parserCalls++;
            return super.getParser();
        }
    }

    public static class TestTarget {

        public String getName() {
            return "test-target";
        }

        public String action(String value) {
            return value;
        }

        public String actionWithParamNameKey(String idempotencyKey) {
            return idempotencyKey;
        }

        public String actionWithRequest(TestRequest request) {
            return request.getUuid().toString();
        }
    }

    public static class TestRequest {

        private final UUID uuid;

        TestRequest(UUID uuid) {
            this.uuid = uuid;
        }

        public UUID getUuid() {
            return uuid;
        }
    }
}
