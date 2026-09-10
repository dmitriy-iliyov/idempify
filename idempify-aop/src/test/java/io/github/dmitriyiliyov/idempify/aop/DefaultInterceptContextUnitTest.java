package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.ExternalOperationCallback;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultInterceptContextUnitTest {

    @Mock
    ExternalOperationCallback operationCallback;

    @Mock
    RequestContext requestContext;

    ResultType operationResultType = ResultType.ofClass(String.class);
    OperationMetadata operationMetadata = TestOperationMetadata.builder().build();

    @Test
    @DisplayName("UT constructor when operationResultType is null should throw NullPointerException")
    void constructor_whenOperationResultTypeIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext(null, operationCallback, UUID.randomUUID(), requestContext, operationMetadata))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationResultType cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationCallback is null should throw NullPointerException")
    void constructor_whenOperationCallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext(operationResultType, null, UUID.randomUUID(), requestContext, operationMetadata))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationCallback cannot be null");
    }

    @Test
    @DisplayName("UT constructor when requestContext is null should throw NullPointerException")
    void constructor_whenRequestContextIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext(operationResultType, operationCallback, UUID.randomUUID(), null, operationMetadata))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("requestContext cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationMetadata is null should throw NullPointerException")
    void constructor_whenOperationMetadataIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultInterceptContext(operationResultType, operationCallback, UUID.randomUUID(), requestContext, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationMetadata cannot be null");
    }

    @Test
    @DisplayName("UT constructor when idempotencyKey is null should create context")
    void constructor_whenIdempotencyKeyIsNull_shouldCreateContext() {
        // when
        DefaultInterceptContext tested = new DefaultInterceptContext(
                operationResultType, operationCallback, null, requestContext, operationMetadata);

        // then
        assertThat(tested.getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("UT constructor when valid arguments provided should create context")
    void constructor_whenValidArgumentsProvided_shouldCreateContext() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        // when
        DefaultInterceptContext tested = new DefaultInterceptContext(
                operationResultType, operationCallback, idempotencyKey, requestContext, operationMetadata);

        // then
        assertThat(tested.getOperationResultType()).isEqualTo(operationResultType);
        assertThat(tested.getOperationCallback()).isSameAs(operationCallback);
        assertThat(tested.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(tested.getRequestContext()).isSameAs(requestContext);
        assertThat(tested.getOperationMetadata()).isSameAs(operationMetadata);
    }

    @Test
    @DisplayName("UT getOperationCallback() when called should return the callback without running it")
    void getOperationCallback_whenCalled_shouldReturnCallbackWithoutRunningIt() {
        // given
        DefaultInterceptContext tested = new DefaultInterceptContext(
                operationResultType, operationCallback, null, requestContext, operationMetadata);

        // when
        ExternalOperationCallback result = tested.getOperationCallback();

        // then
        assertThat(result).isSameAs(operationCallback);
        verifyNoInteractions(operationCallback);
    }

    @Test
    @DisplayName("UT getOperationCallback() when the returned callback is called should invoke the underlying operation")
    void getOperationCallback_whenReturnedCallbackIsCalled_shouldInvokeUnderlyingOperation() throws Throwable {
        // given
        String response = "response";
        DefaultInterceptContext tested = new DefaultInterceptContext(
                operationResultType, operationCallback, null, requestContext, operationMetadata);

        when(operationCallback.call()).thenReturn(response);

        // when
        Object result = tested.getOperationCallback().call();

        // then
        assertThat(result).isEqualTo(response);
        verify(operationCallback, times(1)).call();
    }
}
