package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.IdempotentOperation;
import io.github.dmitriyiliyov.idempify.core.IdempotentProcessor;
import io.github.dmitriyiliyov.idempify.core.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentInterceptorUnitTest {

    @Mock
    KeyExtractor keyExtractor;

    @Mock
    FingerprintManager fingerprintManager;

    @Mock
    IdempotentProcessor processor;

    @InjectMocks
    DefaultIdempotentInterceptor tested;

    @Test
    @DisplayName("UT constructor when keyExtractor is null should throw NullPointerException")
    void constructor_whenKeyExtractorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentInterceptor(null, fingerprintManager, processor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyExtractor cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintManager is null should throw NullPointerException")
    void constructor_whenFingerprintManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentInterceptor(keyExtractor, null, processor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when processor is null should throw NullPointerException")
    void constructor_whenProcessorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentInterceptor(keyExtractor, fingerprintManager, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processor cannot be null");
    }

    @Test
    @DisplayName("UT intercept() when context idempotencyKey is present should use it without calling keyExtractor")
    void intercept_whenIdempotencyKeyIsPresent_shouldUseItWithoutCallingKeyExtractor() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        String response = "response";

        when(context.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.REJECT);
        when(context.useFingerprint()).thenReturn(false);
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);
        when(processor.process(any(OperationMetadata.class), eq(String.class), eq(operation))).thenReturn(response);

        // when
        String result = tested.intercept(context);

        // then
        assertThat(result).isEqualTo(response);
        verifyNoInteractions(keyExtractor);
        verify(processor, times(1)).process(any(OperationMetadata.class), eq(String.class), eq(operation));
    }

    @Test
    @DisplayName("UT intercept() when context idempotencyKey is null should extract key using keyExtractor")
    void intercept_whenIdempotencyKeyIsNull_shouldExtractKeyUsingKeyExtractor() {
        // given
        UUID extractedKey = UUID.randomUUID();
        String headerName = "header-name";
        RequestContext requestContext = mock(RequestContext.class);
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);

        when(context.getIdempotencyKey()).thenReturn(null);
        when(context.getHeaderName()).thenReturn(headerName);
        when(context.getRequestContext()).thenReturn(requestContext);
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.REJECT);
        when(context.useFingerprint()).thenReturn(false);
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);
        when(keyExtractor.extract(headerName, requestContext)).thenReturn(extractedKey);

        // when
        tested.intercept(context);

        // then
        verify(keyExtractor, times(1)).extract(headerName, requestContext);
    }

    @Test
    @DisplayName("UT intercept() when conflictHandleStrategy is CUSTOM should set conflictHandlerClass on metadata")
    void intercept_whenConflictHandleStrategyIsCustom_shouldSetConflictHandlerClassOnMetadata() {
        // given
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        Class<? extends ConflictHandler> handlerClass = TestConflictHandler.class;
        ArgumentCaptor<OperationMetadata> captor = ArgumentCaptor.forClass(OperationMetadata.class);

        when(context.getIdempotencyKey()).thenReturn(UUID.randomUUID());
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.CUSTOM);
        doReturn(handlerClass).when(context).getConflictHandlerClass();
        when(context.useFingerprint()).thenReturn(false);
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(captor.capture(), eq(String.class), eq(operation));
        assertThat(captor.getValue().getConflictHandlerClass()).isEqualTo(handlerClass);
    }

    @Test
    @DisplayName("UT intercept() when conflictHandleStrategy is not CUSTOM should not set conflictHandlerClass on metadata")
    void intercept_whenConflictHandleStrategyIsNotCustom_shouldNotSetConflictHandlerClassOnMetadata() {
        // given
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        ArgumentCaptor<OperationMetadata> captor = ArgumentCaptor.forClass(OperationMetadata.class);

        when(context.getIdempotencyKey()).thenReturn(UUID.randomUUID());
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.REJECT);
        when(context.useFingerprint()).thenReturn(false);
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(captor.capture(), eq(String.class), eq(operation));
        assertThat(captor.getValue().getConflictHandlerClass()).isNull();
        verify(context, never()).getConflictHandlerClass();
    }

    @Test
    @DisplayName("UT intercept() when useFingerprint is true should generate fingerprint and set it on metadata")
    void intercept_whenUseFingerprintIsTrue_shouldGenerateFingerprintAndSetItOnMetadata() {
        // given
        RequestContext requestContext = mock(RequestContext.class);
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        Class<? extends FingerprintPolicy> policyClass = TestFingerprintPolicy.class;
        String fingerprint = "fingerprint";
        ArgumentCaptor<OperationMetadata> captor = ArgumentCaptor.forClass(OperationMetadata.class);

        when(context.getIdempotencyKey()).thenReturn(UUID.randomUUID());
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.REJECT);
        when(context.useFingerprint()).thenReturn(true);
        when(context.getRequestContext()).thenReturn(requestContext);
        doReturn(policyClass).when(context).getFingerprintPolicyClass();
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);
        when(fingerprintManager.generate(requestContext, policyClass)).thenReturn(fingerprint);

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(captor.capture(), eq(String.class), eq(operation));
        OperationMetadata metadata = captor.getValue();
        assertThat(metadata.useFingerprint()).isTrue();
        assertThat(metadata.getFingerprint()).isEqualTo(fingerprint);
        assertThat(metadata.getFingerprintPolicyClass()).isEqualTo(policyClass);
    }

    @Test
    @DisplayName("UT intercept() when useFingerprint is false should not call fingerprintManager")
    void intercept_whenUseFingerprintIsFalse_shouldNotCallFingerprintManager() {
        // given
        InterceptContext<String> context = mock(InterceptContext.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);

        when(context.getIdempotencyKey()).thenReturn(UUID.randomUUID());
        when(context.getTimeUnit()).thenReturn(TimeUnit.HOURS);
        when(context.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.REJECT);
        when(context.useFingerprint()).thenReturn(false);
        when(context.getOperationResultType()).thenReturn(String.class);
        when(context.getOperation()).thenReturn(operation);

        // when
        tested.intercept(context);

        // then
        verifyNoInteractions(fingerprintManager);
    }

    private static class TestConflictHandler implements ConflictHandler {
        @Override
        public <T> Optional<T> handle(UUID idempotencyKey, Class<T> c) {
            return Optional.empty();
        }

        @Override
        public ConflictHandleStrategy getStrategy() {
            return ConflictHandleStrategy.CUSTOM;
        }
    }

    private static class TestFingerprintPolicy implements FingerprintPolicy {
        @Override
        public String generate(RequestContext context) {
            return "fingerprint";
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
