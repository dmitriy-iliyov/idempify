package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.DefaultFingerprintManager;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DefaultFingerprintManagerUnitTests {

    @Test
    @DisplayName("UT constructor when policies is null should throw NullPointerException")
    void constructor_whenPoliciesIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultFingerprintManager(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("policies cannot be null");
    }

    @Test
    @DisplayName("UT generate() when policy exists should return generated fingerprint")
    void generate_whenPolicyExists_shouldReturnFingerprint() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        RequestContext context = mock(RequestContext.class);
        String expectedFingerprint = "fingerprint";

        when(policy.generate(context)).thenReturn(expectedFingerprint);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of(policy));

        // when
        String result = tested.generate(context, policy.getClass());

        // then
        assertThat(result).isEqualTo(expectedFingerprint);

        verify(policy, times(1)).generate(context);
        verifyNoMoreInteractions(policy);
    }

    @Test
    @DisplayName("UT generate() when policy does not exist should throw IllegalStateException")
    void generate_whenPolicyDoesNotExist_shouldThrowIllegalStateException() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        RequestContext context = mock(RequestContext.class);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of());

        // when / then
        assertThatThrownBy(() -> tested.generate(context, policy.getClass()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FingerprintPolicy for class");
    }

    @Test
    @DisplayName("UT compareWith() when policy exists should return comparison result")
    void compareWith_whenPolicyExists_shouldReturnComparisonResult() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        String previous = "previous";
        String current = "current";

        when(policy.compare(previous, current)).thenReturn(true);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of(policy));

        // when
        boolean result = tested.compareWith(previous, current, policy.getClass());

        // then
        assertTrue(result);

        verify(policy, times(1)).compare(previous, current);
        verifyNoMoreInteractions(policy);
    }

    @Test
    @DisplayName("UT compareWith() when policy does not exist should throw IllegalStateException")
    void compareWith_whenPolicyDoesNotExist_shouldThrowIllegalStateException() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of());

        // when / then
        assertThatThrownBy(() -> tested.compareWith("previous", "current", policy.getClass()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FingerprintPolicy for class");
    }

    @Test
    @DisplayName("UT handleMismatch() when policy exists should call handle method")
    void handleMismatch_whenPolicyExists_shouldCallHandleMethod() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        FingerprintMismatchContext context = mock(FingerprintMismatchContext.class);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of(policy));

        // when
        tested.handleMismatch(context, policy.getClass());

        // then
        verify(policy, times(1)).handle(context);
        verifyNoMoreInteractions(policy);
    }

    @Test
    @DisplayName("UT handleMismatch() when policy does not exist should throw IllegalStateException")
    void handleMismatch_whenPolicyDoesNotExist_shouldThrowIllegalStateException() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        FingerprintMismatchContext context = mock(FingerprintMismatchContext.class);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of());

        // when / then
        assertThatThrownBy(() -> tested.handleMismatch(context, policy.getClass()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FingerprintPolicy for class");
    }

    @Test
    @DisplayName("UT constructor when policies contains multiple policies should create manager with all policies")
    void constructor_whenPoliciesContainsMultiplePolicies_shouldCreateManager() {
        // given
        FingerprintPolicy firstPolicy = new FirstTestFingerprintPolicy();
        FingerprintPolicy secondPolicy = new SecondTestFingerprintPolicy();

        RequestContext context = mock(RequestContext.class);

        DefaultFingerprintManager tested = new DefaultFingerprintManager(List.of(firstPolicy, secondPolicy));

        // when
        String firstResult = tested.generate(context, FirstTestFingerprintPolicy.class);
        String secondResult = tested.generate(context, SecondTestFingerprintPolicy.class);

        // then
        assertThat(firstResult).isEqualTo("first");
        assertThat(secondResult).isEqualTo("second");
    }

    @Test
    @DisplayName("UT constructor when policies contains duplicate classes should throw IllegalStateException")
    void constructor_whenPoliciesContainsDuplicateClasses_shouldThrowIllegalStateException() {
        // given
        FingerprintPolicy firstPolicy = mock(FingerprintPolicy.class);
        FingerprintPolicy secondPolicy = mock(FingerprintPolicy.class);

        // when / then
        assertThatThrownBy(() -> new DefaultFingerprintManager(List.of(firstPolicy, secondPolicy)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static class FirstTestFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "first";
        }

        @Override
        public boolean compare(String previous, String current) {
            return previous.equals(current);
        }

        @Override
        public void handle(FingerprintMismatchContext context) {
        }
    }

    private static class SecondTestFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "second";
        }

        @Override
        public boolean compare(String previous, String current) {
            return previous.equals(current);
        }

        @Override
        public void handle(FingerprintMismatchContext context) {
        }
    }
}