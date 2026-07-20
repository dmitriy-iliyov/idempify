package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentOperation;
import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.conflict.RejectConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultInterceptContextUnitTest {

    @Mock
    IdempotentOperation<Object> operation;

    @Mock
    RequestContext requestContext;

    Class<String> resultType = String.class;
    String headerName = "header-name";
    long ttl = 24L;
    TimeUnit timeUnit = TimeUnit.HOURS;
    ConflictHandleStrategy conflictHandleStrategy = ConflictHandleStrategy.REJECT;
    Class<? extends ConflictHandler> conflictHandlerClass = RejectConflictHandler.class;
    boolean useFingerprint = true;
    Class<? extends FingerprintPolicy> fingerprintPolicyClass = DefaultFingerprintPolicy.class;

    @Test
    @DisplayName("UT constructor when resultType is null should throw NullPointerException")
    void constructor_whenResultTypeIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(null, operation, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultType cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operation is null should throw NullPointerException")
    void constructor_whenOperationIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, null, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("methodCall cannot be null");
    }

    @Test
    @DisplayName("UT constructor when requestContext is null should throw NullPointerException")
    void constructor_whenRequestContextIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, null, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestContext cannot be null");
    }

    @Test
    @DisplayName("UT constructor when headerName is null should throw NullPointerException")
    void constructor_whenHeaderNameIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, requestContext, null, null, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headerName cannot be null");
    }

    @Test
    @DisplayName("UT constructor when timeUnit is null should throw NullPointerException")
    void constructor_whenTimeUnitIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, null, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timeUnit cannot be null");
    }

    @Test
    @DisplayName("UT constructor when conflictHandleStrategy is null should throw NullPointerException")
    void constructor_whenConflictHandleStrategyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, timeUnit, null, conflictHandlerClass, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandleStrategy cannot be null");
    }

    @Test
    @DisplayName("UT constructor when conflictHandlerClass is null should throw NullPointerException")
    void constructor_whenConflictHandlerClassIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, null, useFingerprint, fingerprintPolicyClass))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandlerClass cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintPolicyClass is null should throw NullPointerException")
    void constructor_whenFingerprintPolicyClassIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintPolicyClass cannot be null");
    }

    @Test
    @DisplayName("UT constructor when idempotentKey is null should create context")
    void constructor_whenIdempotentKeyIsNull_shouldCreateContext() {
        // when
        DefaultInterceptContext<String> tested = new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass);

        // then
        assertThat(tested.getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("UT constructor when valid arguments provided should create context")
    void constructor_whenValidArgumentsProvided_shouldCreateContext() {
        // given
        UUID idempotentKey = UUID.randomUUID();

        // when
        DefaultInterceptContext<String> tested = new DefaultInterceptContext<>(resultType, operation, requestContext, idempotentKey, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass);

        // then
        assertThat(tested.getOperationResultType()).isEqualTo(resultType);
        assertThat(tested.getRequestContext()).isEqualTo(requestContext);
        assertThat(tested.getIdempotencyKey()).isEqualTo(idempotentKey);
        assertThat(tested.getHeaderName()).isEqualTo(headerName);
        assertThat(tested.getTtl()).isEqualTo(ttl);
        assertThat(tested.getTimeUnit()).isEqualTo(timeUnit);
        assertThat(tested.getConflictHandleStrategy()).isEqualTo(conflictHandleStrategy);
        assertThat(tested.getConflictHandlerClass()).isEqualTo(conflictHandlerClass);
        assertThat(tested.useFingerprint()).isEqualTo(useFingerprint);
        assertThat(tested.getFingerprintPolicyClass()).isEqualTo(fingerprintPolicyClass);
    }

    @Test
    @DisplayName("UT getOperation() when called should invoke underlying operation and return casted result")
    void getOperation_whenCalled_shouldInvokeUnderlyingOperationAndReturnCastedResult() throws Throwable {
        // given
        String response = "response";
        DefaultInterceptContext<String> tested = new DefaultInterceptContext<>(resultType, operation, requestContext, null, headerName, ttl, timeUnit, conflictHandleStrategy, conflictHandlerClass, useFingerprint, fingerprintPolicyClass);

        when(operation.call()).thenReturn(response);

        // when
        String result = tested.getOperation().call();

        // then
        assertThat(result).isEqualTo(response);
        verify(operation, times(1)).call();
    }
}
