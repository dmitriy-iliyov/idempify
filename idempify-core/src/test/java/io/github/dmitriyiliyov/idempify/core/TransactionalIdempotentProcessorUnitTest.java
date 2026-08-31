package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalIdempotentProcessorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Duration TTL = Duration.parse(IdempifyDefaults.TTL_VALUE);
    private static final Instant EXPIRES_AT = TestClock.EPOCH.plus(TTL);

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    TransactionalOperationManager operationManager;

    @Mock
    OperationStateChannel channel;

    @Mock
    IdempotencyEventListener eventListener;

    @InjectMocks
    TransactionalIdempotentProcessor tested;

    @Test
    @DisplayName("UT constructor when transactionTemplate is null should throw NullPointerException")
    void constructor_whenTransactionTemplateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(null, operationManager, channel, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor when operationManager is null should throw NullPointerException")
    void constructor_whenOperationManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(transactionTemplate, null, channel, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operationManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when channel is null should throw NullPointerException")
    void constructor_whenChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(transactionTemplate, operationManager, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalIdempotentProcessor(transactionTemplate, operationManager, channel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT getType() should return TRANSACTIONAL")
    void getType_shouldReturnTransactional() {
        assertThat(tested.getType()).isEqualTo(ProcessorType.TRANSACTIONAL);
    }

    @Test
    @DisplayName("UT process() when the operation is already processed should return the stored result without running the operation")
    void process_whenOperationIsAlreadyProcessed_shouldReturnStoredResultWithoutRunningOperation() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.PROCESSED, true, "replayed"));

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("replayed");
        assertThat(callback.calls).isZero();
        verify(operationManager, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("UT process() when the operation was only claimed should run it and complete it")
    void process_whenOperationWasOnlyClaimed_shouldRunItAndCompleteIt() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));
        when(operationManager.complete(KEY, TTL, "fresh"))
                .thenReturn(detail(OperationStatus.PROCESSED, false, "fresh"));

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("fresh");
        assertThat(callback.calls).isEqualTo(1);
        verify(operationManager, times(1)).complete(KEY, TTL, "fresh");
    }

    @Test
    @DisplayName("UT process() when the operation ran should count a success and not a duplicate")
    void process_whenOperationRan_shouldCountSuccessAndNotDuplicate() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));
        when(operationManager.complete(KEY, TTL, "fresh"))
                .thenReturn(detail(OperationStatus.PROCESSED, false, "fresh"));

        // when
        tested.process(context, metadata);

        // then
        verify(eventListener, times(1)).onSuccess();
        verify(eventListener, never()).onDuplicate();
    }

    @Test
    @DisplayName("UT process() when the operation is replayed should count a duplicate and not a success")
    void process_whenOperationIsReplayed_shouldCountDuplicateAndNotSuccess() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.PROCESSED, true, "replayed"));

        // when
        tested.process(context, metadata);

        // then
        verify(eventListener, times(1)).onDuplicate();
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation returned null should replay that null without running it again")
    void process_whenOperationReturnedNull_shouldReplayThatNullWithoutRunningItAgain() {
        // given
        RecordingCallback callback = new RecordingCallback("fresh");
        OperationContext<String> context = context(callback);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.PROCESSED, true, null));

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isNull();
        assertThat(callback.calls).isZero();
    }

    @Test
    @DisplayName("UT process() when the transaction is through should publish what it recorded about the operation")
    void process_whenTransactionIsThrough_shouldPublishWhatItRecordedAboutOperation() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();
        OperationDetail<String> completed = detail(OperationStatus.PROCESSED, false, "fresh");

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));
        when(operationManager.complete(KEY, TTL, "fresh")).thenReturn(completed);

        // when
        tested.process(context, metadata);

        // then
        verify(channel, times(1)).publish(completed);
    }

    @Test
    @DisplayName("UT process() when the operation is replayed should publish the replayed state too")
    void process_whenOperationIsReplayed_shouldPublishReplayedStateToo() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();
        OperationDetail<String> replayed = detail(OperationStatus.PROCESSED, true, "replayed");

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata)).thenReturn(replayed);

        // when
        tested.process(context, metadata);

        // then
        verify(channel, times(1)).publish(replayed);
    }

    @Test
    @DisplayName("UT process() when the operation throws a runtime exception should let it out unchanged")
    void process_whenOperationThrowsRuntimeException_shouldLetItOutUnchanged() {
        // given
        IllegalStateException thrown = new IllegalStateException("business blew up");
        OperationContext<String> context = context(new ThrowingCallback(thrown));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(thrown);

        verify(operationManager, never()).complete(any(), any(), any());
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
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Surrounded method throws")
                .hasCause(thrown);

        verify(operationManager, never()).complete(any(), any(), any());
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
        verify(operationManager, never()).complete(any(), any(), any());
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation fails should tell the transport nothing")
    void process_whenOperationFails_shouldTellTransportNothing() {
        // given
        OperationContext<String> context = context(new ThrowingCallback(new IllegalStateException("business blew up")));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));

        // when
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IllegalStateException.class);

        // then
        verifyNoInteractions(channel);
    }

    @Test
    @DisplayName("UT process() when the transaction itself fails should report the failure and tell the transport nothing")
    void process_whenTransactionItselfFails_shouldReportFailureAndTellTransportNothing() {
        // given
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().build();
        TransactionSystemException thrown = new TransactionSystemException("commit failed");

        when(transactionTemplate.execute(any())).thenThrow(thrown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(thrown);

        verifyNoInteractions(channel);
        verify(eventListener, times(1)).onException();
        verify(eventListener, never()).onSuccess();
    }

    @Test
    @DisplayName("UT process() when the operation throws should report the failure to the event listener")
    void process_whenOperationThrows_shouldReportFailureToEventListener() {
        // given
        OperationContext<String> context = context(new ThrowingCallback(new IllegalStateException("business blew up")));
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));

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
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));
        when(operationManager.complete(KEY, TTL, "fresh"))
                .thenReturn(detail(OperationStatus.PROCESSED, false, "fresh"));

        // when
        tested.process(context, metadata);

        // then
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() should complete the operation under the call site's own ttl")
    void process_shouldCompleteOperationUnderCallSitesOwnTtl() {
        // given
        Duration callSiteTtl = Duration.ofMinutes(30);
        OperationContext<String> context = context(new RecordingCallback("fresh"));
        OperationMetadata metadata = TestOperationMetadata.builder().ttl(callSiteTtl).build();

        runCallbackInTransaction();
        when(operationManager.startOrReply(context, metadata))
                .thenReturn(detail(OperationStatus.IN_PROCESS, false, null));
        when(operationManager.complete(KEY, callSiteTtl, "fresh"))
                .thenReturn(detail(OperationStatus.PROCESSED, false, "fresh"));

        // when
        String result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("fresh");
        verify(operationManager, times(1)).complete(KEY, callSiteTtl, "fresh");
    }

    private void runCallbackInTransaction() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));
    }

    private OperationContext<String> context(ExternalOperationCallback<String> callback) {
        return new DefaultOperationContext<>(String.class, callback, KEY, "fingerprint");
    }

    private OperationDetail<String> detail(OperationStatus status, boolean replayed, String result) {
        return new DefaultOperationDetail<>(KEY, status, replayed, result, EXPIRES_AT);
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
