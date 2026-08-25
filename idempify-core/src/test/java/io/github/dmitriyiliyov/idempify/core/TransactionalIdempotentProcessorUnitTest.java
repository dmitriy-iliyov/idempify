package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalIdempotentProcessorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    TransactionalOperationManager operationManager;

    @Mock
    IdempotencyEventListener eventListener;

    @InjectMocks
    TransactionalIdempotentProcessor tested;

    @Test
    @DisplayName("UT constructor when transactionTemplate is null should throw NullPointerException")
    void constructor_whenTransactionTemplateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(null, operationManager, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationManager is null should throw NullPointerException")
    void constructor_whenOperationManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(transactionTemplate, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(transactionTemplate, operationManager, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT getType() should return TRANSACTIONAL")
    void getType_shouldReturnTransactional() {
        assertThat(tested.getType()).isEqualTo(ProcessorType.TRANSACTIONAL);
    }

    @Test
    @DisplayName("UT process() when the operation manager replies should return that reply without running the operation")
    void process_whenOperationManagerReplies_shouldReturnThatReplyWithoutRunningOperation() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.of("replayed"));

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("replayed");
        assertThat(callback.calls).isZero();
        verify(operationManager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation manager has no reply should run the operation and complete it")
    void process_whenOperationManagerHasNoReply_shouldRunOperationAndCompleteIt() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.empty());
        when(operationManager.complete(KEY, "fresh")).thenReturn("fresh");

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("fresh");
        assertThat(callback.calls).isEqualTo(1);
        verify(operationManager, times(1)).complete(KEY, "fresh");
        verify(eventListener, times(1)).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation throws a runtime exception should let it out unchanged")
    void process_whenOperationThrowsRuntimeException_shouldLetItOutUnchanged() {
        // given
        IllegalStateException thrown = new IllegalStateException("business blew up");
        OperationContext<String> context = context(new ThrowingCallback(thrown));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(thrown);

        verify(operationManager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation throws a checked exception should wrap it in IdempotentProcessingException")
    void process_whenOperationThrowsCheckedException_shouldWrapItInIdempotentProcessingException() {
        // given
        Exception thrown = new Exception("checked blew up");
        OperationContext<String> context = context(new ThrowingCallback(thrown));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Surrounded method throws")
                .hasCause(thrown);

        verify(operationManager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation manager rejects the fingerprint should let the mismatch out without running the operation")
    void process_whenOperationManagerRejectsFingerprint_shouldLetMismatchOutWithoutRunningOperation() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder()
                .fingerprintPolicy(mock(FingerprintPolicy.class))
                .build();
        FingerprintMismatchException thrown = new FingerprintMismatchException(null, "mismatch");

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenThrow(thrown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(thrown);

        assertThat(callback.calls).isZero();
        verify(operationManager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the transaction template skips the callback should return nothing")
    void process_whenTransactionTemplateSkipsCallback_shouldReturnNothing() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        when(transactionTemplate.execute(any())).thenReturn(null);

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT process() when the operation throws should report the failure to the event listener")
    void process_whenOperationThrows_shouldReportFailureToEventListener() {
        // given
        OperationContext<String> context = context(new ThrowingCallback(new IllegalStateException("business blew up")));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.empty());

        // when
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IllegalStateException.class);

        // then
        verify(eventListener, times(1)).onException();
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation manager throws should report the failure to the event listener")
    void process_whenOperationManagerThrows_shouldReportFailureToEventListener() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenThrow(new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS));

        // when
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(OperationStatusMismatchException.class);

        // then
        assertThat(callback.calls).isZero();
        verify(eventListener, times(1)).onException();
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation is completed should report no failure")
    void process_whenOperationIsCompleted_shouldReportNoFailure() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(Optional.empty());
        when(operationManager.complete(KEY, "fresh")).thenReturn("fresh");

        // when
        tested.process(context, metadata);

        // then
        verify(eventListener, never()).onException();
    }

    private void runCallbackInTransaction() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));
    }

    private OperationContext<String> context(ExternalOperationCallback<String> callback) {
        return new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint");
    }

    private static final class RecordingCallback implements ExternalOperationCallback<String> {

        private final String result;
        private int calls;

        private RecordingCallback(String result) {
            this.result = result;
        }

        @Override
        public String call() {
            calls++;
            return result;
        }
    }

    private record ThrowingCallback(Throwable thrown) implements ExternalOperationCallback<String> {

        @Override
        public String call() throws Throwable {
            throw thrown;
        }
    }
}
