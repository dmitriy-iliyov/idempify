package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentInterceptorUnitTest {

    @Mock
    KeyExtractor keyExtractor;

    @Mock
    IdempotentProcessor processor;

    @Mock
    FingerprintPolicy fingerprintPolicy;

    @Mock
    RequestContext requestContext;

    @Mock
    ExternalOperationCallback operationCallback;

    @InjectMocks
    DefaultIdempotentInterceptor tested;

    @Test
    @DisplayName("UT constructor when keyExtractor is null should throw NullPointerException")
    void constructor_whenKeyExtractorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentInterceptor(null, processor))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyExtractor cannot be null");
    }

    @Test
    @DisplayName("UT constructor when processor is null should throw NullPointerException")
    void constructor_whenProcessorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentInterceptor(keyExtractor, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processor cannot be null");
    }

    @Test
    @DisplayName("UT intercept() when context carries an idempotencyKey should use it without calling keyExtractor")
    void intercept_whenContextCarriesIdempotencyKey_shouldUseItWithoutCallingKeyExtractor() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        InterceptContext context = interceptContext(idempotencyKey, fingerprintingMetadata());
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");

        // when
        tested.intercept(context);

        // then
        verifyNoInteractions(keyExtractor);
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("UT intercept() when context has no idempotencyKey should extract it from the request")
    void intercept_whenContextHasNoIdempotencyKey_shouldExtractItFromRequest() {
        // given
        UUID extractedKey = UUID.randomUUID();
        OperationMetadata metadata = fingerprintingMetadata();
        InterceptContext context = interceptContext(null, metadata);
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        when(keyExtractor.extract(metadata.getHeaderName(), requestContext)).thenReturn(extractedKey);
        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");

        // when
        tested.intercept(context);

        // then
        verify(keyExtractor, times(1)).extract(metadata.getHeaderName(), requestContext);
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(extractedKey);
    }

    @Test
    @DisplayName("UT intercept() when the key comes from an expression should not look for it in the request")
    void intercept_whenKeyComesFromExpression_shouldNotLookForItInRequest() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        InterceptContext context = interceptContext(idempotencyKey, expressionKeyedMetadata());
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        // when
        tested.intercept(context);

        // then
        verifyNoInteractions(keyExtractor);
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("UT intercept() when the expression supplied no key should refuse rather than fall back to the request")
    void intercept_whenExpressionSuppliedNoKey_shouldRefuseRatherThanFallBackToRequest() {
        // given
        InterceptContext context = interceptContext(null, expressionKeyedMetadata());

        // when / then
        assertThatThrownBy(() -> tested.intercept(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Operation has no idempotency key");
        verifyNoInteractions(keyExtractor, processor);
    }

    @Test
    @DisplayName("UT intercept() when metadata uses fingerprint should generate it with the metadata policy")
    void intercept_whenMetadataUsesFingerprint_shouldGenerateItWithMetadataPolicy() {
        // given
        String fingerprint = "fingerprint";
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        when(fingerprintPolicy.generate(requestContext)).thenReturn(fingerprint);

        // when
        tested.intercept(context);

        // then
        verify(fingerprintPolicy, times(1)).generate(requestContext);
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getFingerprint()).contains(fingerprint);
    }

    @Test
    @DisplayName("UT intercept() when metadata does not use fingerprint should pass an empty fingerprint")
    void intercept_whenMetadataDoesNotUseFingerprint_shouldPassEmptyFingerprint() {
        // given
        InterceptContext context = interceptContext(UUID.randomUUID(), plainMetadata());
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getFingerprint()).isEmpty();
        verifyNoInteractions(fingerprintPolicy);
    }

    @Test
    @DisplayName("UT intercept() when the fingerprint policy returns null should throw NullPointerException")
    void intercept_whenFingerprintPolicyReturnsNull_shouldThrowNullPointerException() {
        // given
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());

        when(fingerprintPolicy.generate(requestContext)).thenReturn(null);

        // when // then
        assertThatThrownBy(() -> tested.intercept(context))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprint cannot be null");
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("UT intercept() when the fingerprint policy returns a blank value should throw IllegalArgumentException")
    void intercept_whenFingerprintPolicyReturnsBlankValue_shouldThrowIllegalArgumentException() {
        // given
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());

        when(fingerprintPolicy.generate(requestContext)).thenReturn("   ");

        // when // then
        assertThatThrownBy(() -> tested.intercept(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint cannot be empty or blank");
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("UT intercept() when called should pass the result type and the callback through untouched")
    void intercept_whenCalled_shouldPassResultTypeAndCallbackThroughUntouched() {
        // given
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());
        ArgumentCaptor<OperationContext> captor = operationContextCaptor();

        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(captor.capture(), any(OperationMetadata.class));
        assertThat(captor.getValue().getOperationResultType()).isEqualTo(ResultType.ofClass(String.class));
        assertThat(captor.getValue().getOperationCallback()).isSameAs(operationCallback);
    }

    @Test
    @DisplayName("UT intercept() when called should pass the context metadata to the processor")
    void intercept_whenCalled_shouldPassContextMetadataToProcessor() {
        // given
        OperationMetadata metadata = fingerprintingMetadata();
        InterceptContext context = interceptContext(UUID.randomUUID(), metadata);

        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");

        // when
        tested.intercept(context);

        // then
        verify(processor, times(1)).process(any(), eq(metadata));
    }

    @Test
    @DisplayName("UT intercept() when processor returns a result should return it to the caller")
    void intercept_whenProcessorReturnsResult_shouldReturnItToCaller() {
        // given
        String response = "response";
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());

        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");
        when(processor.process(any(), any())).thenReturn(response);

        // when
        Object result = tested.intercept(context);

        // then
        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("UT intercept() when processor throws should let the exception through")
    void intercept_whenProcessorThrows_shouldLetExceptionThrough() {
        // given
        InterceptContext context = interceptContext(UUID.randomUUID(), fingerprintingMetadata());

        when(fingerprintPolicy.generate(requestContext)).thenReturn("fingerprint");
        when(processor.process(any(), any())).thenThrow(new IllegalStateException("boom"));

        // when // then
        assertThatThrownBy(() -> tested.intercept(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    private InterceptContext interceptContext(UUID idempotencyKey, OperationMetadata metadata) {
        return new DefaultInterceptContext(
                ResultType.ofClass(String.class),
                operationCallback,
                idempotencyKey,
                requestContext,
                metadata
        );
    }

    private OperationMetadata plainMetadata() {
        return TestOperationMetadata.builder()
                .useFingerprint(false)
                .build();
    }

    private OperationMetadata expressionKeyedMetadata() {
        return TestOperationMetadata.builder()
                .headerName(null)
                .useFingerprint(false)
                .build();
    }

    private OperationMetadata fingerprintingMetadata() {
        return TestOperationMetadata.builder()
                .useFingerprint(true)
                .fingerprintPolicy(fingerprintPolicy)
                .build();
    }

    private ArgumentCaptor<OperationContext> operationContextCaptor() {
        return ArgumentCaptor.forClass(OperationContext.class);
    }
}
