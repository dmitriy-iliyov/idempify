package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.OperationInProgressException;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * What the processor decides on every way out of a call, over a manager and a transaction manager that only
 * answer: whether the business method runs in a transaction of its own, what the caller is thrown, which outcome
 * is counted, what the transport is told, and whether the claim is given back. A failure of the call itself is
 * counted by the decorator above the processor, so here it is only never counted twice. That the effect and the record really commit or roll
 * back together is the component test's.
 */
@ExtendWith(MockitoExtension.class)
class TransactionalNonBlockingIdempotentProcessorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private NonBlockingOperationManager manager;
    @Mock
    private OperationStateChannel stateChannel;
    @Mock
    private IdempotencyEventListener eventListener;
    @Mock
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        Thread.interrupted();
    }

    @Test
    @DisplayName("UT constructor() when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalNonBlockingIdempotentProcessor(
                null, stateChannel, eventListener, template(Propagation.REQUIRED)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("manager cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when stateChannel is null should throw NullPointerException")
    void constructor_whenStateChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalNonBlockingIdempotentProcessor(
                manager, null, eventListener, template(Propagation.REQUIRED)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stateChannel cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalNonBlockingIdempotentProcessor(
                manager, stateChannel, null, template(Propagation.REQUIRED)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when transactionTemplate is null should throw NullPointerException")
    void constructor_whenTransactionTemplateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TransactionalNonBlockingIdempotentProcessor(manager, stateChannel, eventListener, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("transactionTemplate cannot be null");
    }

    @ParameterizedTest
    @EnumSource(value = Propagation.class, names = {"SUPPORTS", "NOT_SUPPORTED", "NEVER", "MANDATORY"})
    @DisplayName("UT constructor() when the template cannot open a transaction of its own should throw IllegalArgumentException")
    void constructor_whenTemplateCannotOpenTransactionOfItsOwn_shouldThrowIllegalArgumentException(Propagation propagation) {
        assertThatThrownBy(() -> new TransactionalNonBlockingIdempotentProcessor(
                manager, stateChannel, eventListener, template(propagation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must open a transaction of its own");
    }

    @ParameterizedTest
    @EnumSource(value = Propagation.class, names = {"REQUIRED", "REQUIRES_NEW", "NESTED"})
    @DisplayName("UT constructor() when the template opens a transaction of its own should be built")
    void constructor_whenTemplateOpensTransactionOfItsOwn_shouldBeBuilt(Propagation propagation) {
        assertThatCode(() -> new TransactionalNonBlockingIdempotentProcessor(
                manager, stateChannel, eventListener, template(propagation)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT getType() should serve the transactional non-blocking processor type")
    void getType_shouldServeTransactionalNonBlockingProcessorType() {
        assertThat(tested().getType()).isEqualTo(ProcessorType.TRANSACTIONAL_NON_BLOCKING);
    }

    @Test
    @DisplayName("UT process() when the key is new should run the operation in a transaction, complete it and publish its state")
    void process_whenKeyIsNew_shouldRunOperationInTransactionCompleteItAndPublishItsState() throws Throwable {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail claimed = detail(OperationStatus.IN_PROCESS, true, null);
        SimpleTransactionStatus transaction = givenTransaction();
        givenClaimed(context, metadata, claimed);
        when(manager.complete(claimed, "charged", TTL)).thenReturn(detail(OperationStatus.PROCESSED, true, "charged"));

        // when
        Object result = tested().process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        verify(transactionManager).commit(transaction);
        assertThat(transaction.isRollbackOnly()).isFalse();
        verify(stateChannel).publish(argThat(OperationState::shouldRecordResponse));
        verify(eventListener).onSuccess();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the key already completed should replay its result without opening a transaction")
    void process_whenKeyAlreadyCompleted_shouldReplayItsResultWithoutOpeningTransaction() throws Throwable {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.PROCESSED, false, "charged"));

        // when
        Object result = tested().process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(callback.calls).isZero();
        verifyNoInteractions(transactionManager);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener).onDuplicate();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the key kept a failure should throw that failure again without guarding it")
    void process_whenKeyKeptFailure_shouldThrowThatFailureAgainWithoutGuardingIt() {
        // given
        IllegalStateException kept = new IllegalStateException("card declined");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.EXCEPTIONALLY_PROCESSED, false, kept));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(kept);
        verifyNoInteractions(transactionManager);
        verify(eventListener).onDuplicate();
        verify(manager, never()).rollback(any(), any());
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when called inside a transaction should refuse it before claiming anything")
    void process_whenCalledInsideTransaction_shouldRefuseItBeforeClaimingAnything() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata(List.of())))
                .isInstanceOf(ForeignTransactionException.class)
                .hasMessageContaining("must not run inside a transaction");
        assertThat(callback.calls).isZero();
        verifyNoInteractions(manager, transactionManager, stateChannel);
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when the claim cannot be taken should fail naming the key and open no transaction")
    void process_whenClaimCannotBeTaken_shouldFailNamingKeyAndOpenNoTransaction() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        IllegalStateException storeDown = new IllegalStateException("store down");
        when(manager.tryLock(context, metadata)).thenThrow(storeDown);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isInstanceOf(OperationLockFailedException.class)
                .hasMessageContaining("could not be claimed")
                .hasMessageContaining(KEY.toString())
                .hasCause(storeDown);
        assertThat(callback.calls).isZero();
        verifyNoInteractions(transactionManager, stateChannel);
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when another call holds the claim and the duplicate is refused should touch no record")
    void process_whenAnotherCallHoldsClaimAndDuplicateIsRefused_shouldTouchNoRecord() {
        // given
        OperationInProgressException refusal = new OperationInProgressException("already in progress");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of());
        LockDetail missed = new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(missed);
        when(manager.fetchAndValidate(context, metadata, missed)).thenThrow(refusal);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(refusal);
        verifyNoInteractions(transactionManager);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when the claimed record cannot be read should run nothing, record no response and let the claim go")
    void process_whenClaimedRecordCannotBeRead_shouldRunNothingRecordNoResponseAndLetClaimGo() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        OperationDisappearedException disappeared = new OperationDisappearedException("record is gone");
        LockDetail acquired = new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(acquired);
        when(manager.fetchAndValidate(context, metadata, acquired)).thenThrow(disappeared);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isExactlyInstanceOf(EarlyFailureException.class)
                .hasCause(disappeared);
        assertThat(callback.calls).isZero();
        verifyNoInteractions(transactionManager);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the operation fails with an exception not named in recordFor should roll its transaction back and free the key")
    void process_whenOperationFailsWithExceptionNotNamedInRecordFor_shouldRollItsTransactionBackAndFreeKey() {
        // given
        IllegalArgumentException failure = new IllegalArgumentException("bad amount");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of());
        SimpleTransactionStatus transaction = givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(failure);
        assertThat(transaction.isRollbackOnly()).isTrue();
        verify(manager, never()).complete(any(), any(), any());
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener).onBusinessFailure();
        verify(eventListener, never()).onFailure(any());
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when the operation fails with an exception named in recordFor should roll its transaction back and keep the failure")
    void process_whenOperationFailsWithExceptionNamedInRecordFor_shouldRollItsTransactionBackAndKeepFailure() {
        // given
        IllegalStateException failure = new IllegalStateException("card declined");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of(IllegalStateException.class));
        SimpleTransactionStatus transaction = givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(failure);
        assertThat(transaction.isRollbackOnly()).isTrue();
        verify(manager).completeExceptionally(context, failure, TTL, LOCK_TOKEN);
        verify(manager, never()).rollback(any(), any());
        verify(stateChannel).publish(argThat(OperationState::shouldRecordResponse));
        verify(eventListener).onExceptionallySuccess();
        verify(eventListener, never()).onBusinessFailure();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when a nested call is refused should free the key whatever recordFor says")
    void process_whenNestedCallIsRefused_shouldFreeKeyWhateverRecordForSays() {
        // given
        NestedIdempotencyException refusal = new NestedIdempotencyException();
        OperationContext context = context(() -> {
            throw refusal;
        });
        OperationMetadata metadata = metadata(List.of());
        givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(refusal);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener, never()).onFailure(any());
        verify(eventListener, never()).onBusinessFailure();
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when the operation is interrupted should free the key whatever recordFor says")
    void process_whenOperationIsInterrupted_shouldFreeKeyWhateverRecordForSays() {
        // given
        InterruptedException interruption = new InterruptedException("shutting down");
        OperationContext context = context(() -> {
            throw interruption;
        });
        OperationMetadata metadata = metadata(List.of());
        givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(interruption);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener, never()).onFailure(any());
        verify(eventListener, never()).onBusinessFailure();
    }

    @Test
    @DisplayName("UT process() when the operation fails on an interrupted thread should free the key and leave the flag set")
    void process_whenOperationFailsOnInterruptedThread_shouldFreeKeyAndLeaveFlagSet() {
        // given
        IllegalStateException failure = new IllegalStateException("gateway call aborted");
        OperationContext context = context(() -> {
            Thread.currentThread().interrupt();
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of());
        givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(failure);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when completing the operation fails should say its outcome was not recorded, roll its transaction back, record no response and let the claim go")
    void process_whenCompletingOperationFails_shouldSayItsOutcomeWasNotRecordedRollItsTransactionBackRecordNoResponseAndLetClaimGo() {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail claimed = detail(OperationStatus.IN_PROCESS, true, null);
        SimpleTransactionStatus transaction = givenTransaction();
        givenClaimed(context, metadata, claimed);
        OperationStatusMismatchException mismatch = new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS);
        when(manager.complete(claimed, "charged", TTL)).thenThrow(mismatch);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isExactlyInstanceOf(ResultNotRecordedException.class)
                .hasCause(mismatch);
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener, never()).onFailure(any());
        verify(eventListener, never()).onSuccess();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when completing the operation throws an error should let that very error out and let the claim go")
    void process_whenCompletingOperationThrowsError_shouldLetThatVeryErrorOutAndLetClaimGo() {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail claimed = detail(OperationStatus.IN_PROCESS, true, null);
        givenTransaction();
        givenClaimed(context, metadata, claimed);
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");
        when(manager.complete(claimed, "charged", TTL)).thenThrow(error);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata)).isSameAs(error);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener, never()).onFailure(any());
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the transaction cannot begin should run nothing, record no response and let the claim go")
    void process_whenTransactionCannotBegin_shouldRunNothingRecordNoResponseAndLetClaimGo() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        CannotCreateTransactionException beginFailure = new CannotCreateTransactionException("begin failed");
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        when(transactionManager.getTransaction(any())).thenThrow(beginFailure);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isExactlyInstanceOf(EarlyFailureException.class)
                .hasMessageContaining(KEY.toString())
                .hasCause(beginFailure);
        assertThat(callback.calls).isZero();
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener, never()).onFailure(any());
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the commit fails should say the outcome was not recorded, record no response and let the claim go")
    void process_whenCommitFails_shouldSayOutcomeWasNotRecordedRecordNoResponseAndLetClaimGo() {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail claimed = detail(OperationStatus.IN_PROCESS, true, null);
        givenTransaction();
        givenClaimed(context, metadata, claimed);
        when(manager.complete(claimed, "charged", TTL)).thenReturn(detail(OperationStatus.PROCESSED, true, "charged"));
        TransactionSystemException commitFailure = new TransactionSystemException("commit failed");
        doThrow(commitFailure).when(transactionManager).commit(any());

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isExactlyInstanceOf(ResultNotRecordedException.class)
                .hasCause(commitFailure);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener, never()).onFailure(any());
        verify(eventListener, never()).onSuccess();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when keeping the failure on the record fails too should say so, record no response and let the claim go")
    void process_whenKeepingFailureOnRecordFailsToo_shouldSaySoRecordNoResponseAndLetClaimGo() {
        // given
        IllegalStateException businessFailure = new IllegalStateException("card declined");
        OperationContext context = context(() -> {
            throw businessFailure;
        });
        OperationMetadata metadata = metadata(List.of(Throwable.class));
        givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        IllegalStateException storeDown = new IllegalStateException("store down");
        doThrow(storeDown).when(manager).completeExceptionally(any(), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasMessageContaining("could not be released or recorded")
                .hasCause(storeDown)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(businessFailure));
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(eventListener, never()).onFailure(any());
        verify(eventListener, never()).onExceptionallySuccess();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when removing the record of a released failure fails should say so and let the claim go")
    void process_whenRemovingRecordOfReleasedFailureFails_shouldSaySoAndLetClaimGo() {
        // given
        IllegalArgumentException businessFailure = new IllegalArgumentException("bad amount");
        OperationContext context = context(() -> {
            throw businessFailure;
        });
        OperationMetadata metadata = metadata(List.of());
        givenTransaction();
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        IllegalStateException storeDown = new IllegalStateException("store down");
        doThrow(storeDown).when(manager).rollback(KEY, LOCK_TOKEN);

        // when / then
        assertThatThrownBy(() -> tested().process(context, metadata))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasCause(storeDown);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when letting the claim go fails should still answer with the operation's result")
    void process_whenLettingClaimGoFails_shouldStillAnswerWithOperationsResult() throws Throwable {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail claimed = detail(OperationStatus.IN_PROCESS, true, null);
        givenTransaction();
        givenClaimed(context, metadata, claimed);
        when(manager.complete(claimed, "charged", TTL)).thenReturn(detail(OperationStatus.PROCESSED, true, "charged"));
        doThrow(new IllegalStateException("store down")).when(manager).unlock(KEY, LOCK_TOKEN);

        // when
        Object result = tested().process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        verify(eventListener).onSuccess();
    }

    private TransactionalNonBlockingIdempotentProcessor tested() {
        return new TransactionalNonBlockingIdempotentProcessor(
                manager,
                stateChannel,
                eventListener,
                template(Propagation.REQUIRED)
        );
    }

    private SimpleTransactionStatus givenTransaction() {
        SimpleTransactionStatus transaction = new SimpleTransactionStatus(true);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        return transaction;
    }

    private void givenClaimed(OperationContext context, OperationMetadata metadata, NonBlockingOperationDetail detail) {
        LockDetail acquired = new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(acquired);
        when(manager.fetchAndValidate(context, metadata, acquired)).thenReturn(detail);
    }

    private TransactionTemplate template(Propagation propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation.value());
        return template;
    }

    private static OperationContext context(BusinessOperationCallback callback) {
        return new DefaultOperationContext(RESULT_TYPE, callback, KEY, null);
    }

    private static OperationMetadata metadata(List<Class<? extends Throwable>> recordFor) {
        return TestOperationMetadata.builder()
                .processorType(ProcessorType.TRANSACTIONAL_NON_BLOCKING)
                .ttl(TTL)
                .lockTtl(Duration.ofMinutes(5))
                .responseCacheConfig(ResponseConfig.builder().recordFor(recordFor).build())
                .build();
    }

    private static NonBlockingOperationDetail detail(OperationStatus status,
                                                     boolean shouldRecordResponse,
                                                     Object result) {
        return new DefaultNonBlockingOperationDetail(true, LOCK_TOKEN, KEY, status, result, shouldRecordResponse);
    }

    private static final class RecordingCallback implements BusinessOperationCallback {

        private int calls;

        @Override
        public Object call() {
            calls++;
            return "charged";
        }
    }
}
