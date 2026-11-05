package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;
import io.github.dmitriyiliyov.idempify.core.conflict.OperationInProgressException;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * The record of this branch outlives the business method, so what the processor owes is what happens to it on
 * every way out: a result is completed onto it, a failure named in {@code recordFor} is kept on the record, any
 * other frees the key, and a claim this call took is given back - unless the outcome could not be
 * written, in which case the claim is kept until its lease runs out.
 */
@ExtendWith(MockitoExtension.class)
class ExternalNonBlockingIdempotentProcessorUnitTest {

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

    @InjectMocks
    private ExternalNonBlockingIdempotentProcessor tested;

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("UT constructor() when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new ExternalNonBlockingIdempotentProcessor(null, stateChannel, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("manager cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when stateChannel is null should throw NullPointerException")
    void constructor_whenStateChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new ExternalNonBlockingIdempotentProcessor(manager, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stateChannel cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new ExternalNonBlockingIdempotentProcessor(manager, stateChannel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT getType() should serve the non-blocking processor type")
    void getType_shouldServeNonBlockingProcessorType() {
        assertThat(tested.getType()).isEqualTo(ProcessorType.EXTERNAL_NON_BLOCKING);
    }

    @Test
    @DisplayName("UT process() when the key is new should run the operation, complete it and publish its state")
    void process_whenKeyIsNew_shouldRunOperationCompleteItAndPublishItsState() throws Throwable {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail completed = detail(OperationStatus.PROCESSED, true, "charged");
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        when(manager.complete(any(), eq("charged"), eq(TTL))).thenReturn(completed);

        // when
        Object result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        verify(stateChannel).publish(argThat(OperationState::shouldRecordResponse));
        verify(eventListener).onSuccess();
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the key already completed should replay its result without running the operation")
    void process_whenKeyAlreadyCompleted_shouldReplayItsResultWithoutRunningOperation() throws Throwable {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        NonBlockingOperationDetail replay = detail(OperationStatus.PROCESSED, false, "charged");
        givenClaimed(context, metadata, replay);

        // when
        Object result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(callback.calls).isZero();
        verify(manager, never()).complete(any(), any(), any());
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
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(kept);
        verify(eventListener).onDuplicate();
        verify(manager, never()).rollback(any(), any());
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when the operation fails with an exception not named in recordFor should free the key and keep nothing")
    void process_whenOperationFailsWithExceptionNotNamedInRecordFor_shouldFreeKeyAndKeepNothing() {
        // given
        IllegalArgumentException failure = new IllegalArgumentException("bad amount");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener).onBusinessFailure();
        verify(eventListener, never()).onFailure(any());
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when the operation fails with an exception named in recordFor should keep it on the record")
    void process_whenOperationFailsWithExceptionNamedInRecordFor_shouldKeepItOnRecord() {
        // given
        IllegalStateException failure = new IllegalStateException("card declined");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of(IllegalStateException.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(manager).completeExceptionally(context, failure, TTL, LOCK_TOKEN);
        verify(manager, never()).rollback(any(), any());
        verify(stateChannel).publish(argThat(OperationState::shouldRecordResponse));
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
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(interruption);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener, never()).onBusinessFailure();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        verify(eventListener, never()).onFailure(any());
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
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(eventListener, never()).onFailure(any());
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
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(refusal);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener, never()).onBusinessFailure();
        verify(eventListener, never()).onFailure(any());
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when the operation fails and its failure is kept should still give the claim back")
    void process_whenOperationFailsAndItsFailureIsKept_shouldStillGiveClaimBack() {
        // given
        OperationContext context = context(() -> {
            throw new IllegalStateException("card declined");
        });
        OperationMetadata metadata = metadata(List.of(Throwable.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isInstanceOf(IllegalStateException.class);
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when another call holds the claim and the duplicate is refused should touch no record")
    void process_whenAnotherCallHoldsClaimAndDuplicateIsRefused_shouldTouchNoRecord() {
        // given
        OperationInProgressException refusal = new OperationInProgressException("already in progress");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of(OperationInProgressException.class));
        LockDetail missed = new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(missed);
        when(manager.fetchAndValidate(context, metadata, missed)).thenThrow(refusal);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(refusal);
        verify(manager, never()).rollback(any(), any());
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onBusinessFailure();
    }

    @Test
    @DisplayName("UT process() when another call holds the claim and the duplicate fails should tell the transport there is nothing to record")
    void process_whenAnotherCallHoldsClaimAndDuplicateFails_shouldTellTransportThereIsNothingToRecord() {
        // given
        ConflictHandlerResultException failure = new ConflictHandlerResultException("handler returned null");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of());
        LockDetail missed = new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(missed);
        when(manager.fetchAndValidate(context, metadata, missed)).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
    }

    @Test
    @DisplayName("UT process() when the claimed record cannot be read should fail as before the operation ran, record no response and let the claim go")
    void process_whenClaimedRecordCannotBeRead_shouldFailAsBeforeRunRecordNoResponseAndLetClaimGo() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        IllegalStateException storeDown = new IllegalStateException("store down");
        LockDetail acquired = new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(acquired);
        when(manager.fetchAndValidate(context, metadata, acquired)).thenThrow(storeDown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isExactlyInstanceOf(EarlyFailureException.class)
                .hasMessageContaining(KEY.toString())
                .hasCause(storeDown);
        assertThat(callback.calls).isZero();
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager).unlock(KEY, LOCK_TOKEN);
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when the claim cannot be taken should fail naming the key and run nothing")
    void process_whenClaimCannotBeTaken_shouldFailNamingKeyAndRunNothing() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        IllegalStateException storeDown = new IllegalStateException("store down");
        when(manager.tryLock(context, metadata)).thenThrow(storeDown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(OperationLockFailedException.class)
                .hasMessageContaining("could not be claimed")
                .hasMessageContaining(KEY.toString())
                .hasCause(storeDown);
        assertThat(callback.calls).isZero();
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when keeping the failure on the record fails too should say so and keep the claim")
    void process_whenKeepingFailureOnRecordFailsToo_shouldSaySoAndKeepClaim() {
        // given
        IllegalStateException businessFailure = new IllegalStateException("card declined");
        OperationContext context = context(() -> {
            throw businessFailure;
        });
        OperationMetadata metadata = metadata(List.of(Throwable.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        IllegalStateException storeDown = new IllegalStateException("store down");
        doThrow(storeDown).when(manager).completeExceptionally(any(), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasMessageContaining("could not be released or recorded")
                .hasCause(storeDown)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(businessFailure));
        verify(eventListener, never()).onBusinessFailure();
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when claiming the key throws an error should let that very error out unwrapped")
    void process_whenClaimingKeyThrowsError_shouldLetThatVeryErrorOutUnwrapped() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");
        when(manager.tryLock(context, metadata)).thenThrow(error);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(error);
        assertThat(callback.calls).isZero();
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when keeping the failure on the record throws an error should let that very error out and keep the claim")
    void process_whenKeepingFailureOnRecordThrowsError_shouldLetThatVeryErrorOutAndKeepClaim() {
        // given
        OperationContext context = context(() -> {
            throw new IllegalStateException("card declined");
        });
        OperationMetadata metadata = metadata(List.of(Throwable.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");
        doThrow(error).when(manager).completeExceptionally(any(), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(error);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when completing the operation fails should say its outcome was not recorded and keep the claim")
    void process_whenCompletingOperationFails_shouldSayItsOutcomeWasNotRecordedAndKeepClaim() {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        IllegalStateException storeDown = new IllegalStateException("store down");
        when(manager.complete(any(), eq("charged"), eq(TTL))).thenThrow(storeDown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isExactlyInstanceOf(ResultNotRecordedException.class)
                .hasCause(storeDown);
        verify(eventListener, never()).onSuccess();
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onFailure(any());
    }

    @Test
    @DisplayName("UT process() when completing the operation throws an error should let that very error out and keep the claim")
    void process_whenCompletingOperationThrowsError_shouldLetThatVeryErrorOutAndKeepClaim() {
        // given
        OperationContext context = context(() -> "charged");
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");
        when(manager.complete(any(), eq("charged"), eq(TTL))).thenThrow(error);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(error);
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onFailure(any());
    }

    private void givenClaimed(OperationContext context, OperationMetadata metadata, NonBlockingOperationDetail detail) {
        LockDetail acquired = new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(acquired);
        when(manager.fetchAndValidate(context, metadata, acquired)).thenReturn(detail);
    }

    private static OperationContext context(BusinessOperationCallback callback) {
        return new DefaultOperationContext(RESULT_TYPE, callback, KEY, null);
    }

    private static OperationMetadata metadata(List<Class<? extends Throwable>> recordFor) {
        return TestOperationMetadata.builder()
                .processorType(ProcessorType.EXTERNAL_NON_BLOCKING)
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
