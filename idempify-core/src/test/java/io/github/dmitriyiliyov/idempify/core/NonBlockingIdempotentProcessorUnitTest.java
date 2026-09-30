package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;
import io.github.dmitriyiliyov.idempify.core.conflict.IdempotencyConflictException;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
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
 * every way out: a result is completed onto it, a failure named in {@code releaseFor} frees the key, any other
 * failure is kept on the record, and a claim this call took is given back whatever happened.
 */
@ExtendWith(MockitoExtension.class)
class NonBlockingIdempotentProcessorUnitTest {

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
    private NonBlockingIdempotentProcessor tested;

    @Test
    @DisplayName("UT constructor() when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new NonBlockingIdempotentProcessor(null, stateChannel, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("manager cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when stateChannel is null should throw NullPointerException")
    void constructor_whenStateChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new NonBlockingIdempotentProcessor(manager, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stateChannel cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new NonBlockingIdempotentProcessor(manager, stateChannel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT getType() should serve the non-blocking processor type")
    void getType_shouldServeNonBlockingProcessorType() {
        assertThat(tested.getType()).isEqualTo(ProcessorType.NON_BLOCKING);
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
    @DisplayName("UT process() when the operation fails with an exception named in releaseFor should free the key and keep nothing")
    void process_whenOperationFailsWithExceptionNamedInReleaseFor_shouldFreeKeyAndKeepNothing() {
        // given
        IllegalArgumentException failure = new IllegalArgumentException("bad amount");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of(IllegalArgumentException.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(manager).rollback(KEY, LOCK_TOKEN);
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(eventListener).onException();
    }

    @Test
    @DisplayName("UT process() when the operation fails with an exception not named in releaseFor should keep it on the record")
    void process_whenOperationFailsWithExceptionNotNamedInReleaseFor_shouldKeepItOnRecord() {
        // given
        IllegalStateException failure = new IllegalStateException("card declined");
        OperationContext context = context(() -> {
            throw failure;
        });
        OperationMetadata metadata = metadata(List.of(IllegalArgumentException.class));
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(manager).completeExceptionally(context, failure, TTL, LOCK_TOKEN);
        verify(manager, never()).rollback(any(), any());
        verify(stateChannel).publish(argThat(OperationState::shouldRecordResponse));
    }

    @Test
    @DisplayName("UT process() when the operation fails should still give the claim back")
    void process_whenOperationFails_shouldStillGiveClaimBack() {
        // given
        OperationContext context = context(() -> {
            throw new IllegalStateException("card declined");
        });
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isInstanceOf(IllegalStateException.class);
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT process() when another call holds the claim and the duplicate is refused should touch no record")
    void process_whenAnotherCallHoldsClaimAndDuplicateIsRefused_shouldTouchNoRecord() {
        // given
        IdempotencyConflictException refusal = new IdempotencyConflictException("already in progress");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of(IdempotencyConflictException.class));
        LockDetail missed = new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(missed);
        when(manager.fetchAndValidate(context, metadata, missed)).thenThrow(refusal);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(refusal);
        verify(manager, never()).rollback(any(), any());
        verify(manager, never()).completeExceptionally(any(), any(), any(), any());
        verify(manager, never()).unlock(any(), any());
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() when another call holds the claim and the duplicate fails should publish no state")
    void process_whenAnotherCallHoldsClaimAndDuplicateFails_shouldPublishNoState() {
        // given
        ConflictHandlerResultException failure = new ConflictHandlerResultException("handler returned null");
        OperationContext context = context(new RecordingCallback());
        OperationMetadata metadata = metadata(List.of());
        LockDetail missed = new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(missed);
        when(manager.fetchAndValidate(context, metadata, missed)).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata)).isSameAs(failure);
        verify(stateChannel, never()).publish(any());
    }

    @Test
    @DisplayName("UT process() when the claim cannot be taken should fail naming the key and run nothing")
    void process_whenClaimCannotBeTaken_shouldFailNamingKeyAndRunNothing() {
        // given
        RecordingCallback callback = new RecordingCallback();
        OperationContext context = context(callback);
        OperationMetadata metadata = metadata(List.of());
        AssertionError storeDown = new AssertionError("store down");
        when(manager.tryLock(context, metadata)).thenThrow(storeDown);

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessageContaining("could not be claimed")
                .hasMessageContaining(KEY.toString())
                .hasCause(storeDown);
        assertThat(callback.calls).isZero();
        verify(manager, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("UT process() when keeping the failure on the record fails too should say the record was not kept")
    void process_whenKeepingFailureOnRecordFailsToo_shouldSayRecordWasNotKept() {
        // given
        OperationContext context = context(() -> {
            throw new IllegalStateException("card declined");
        });
        OperationMetadata metadata = metadata(List.of());
        givenClaimed(context, metadata, detail(OperationStatus.IN_PROCESS, true, null));
        AssertionError storeDown = new AssertionError("store down");
        doThrow(storeDown).when(manager).completeExceptionally(any(), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> tested.process(context, metadata))
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessageContaining("could not be released or recorded")
                .hasCause(storeDown);
        verify(eventListener).onException();
        verify(stateChannel).publish(argThat(state -> !state.shouldRecordResponse()));
        verify(manager).unlock(KEY, LOCK_TOKEN);
    }

    private void givenClaimed(OperationContext context, OperationMetadata metadata, NonBlockingOperationDetail detail) {
        LockDetail acquired = new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
        when(manager.tryLock(context, metadata)).thenReturn(acquired);
        when(manager.fetchAndValidate(context, metadata, acquired)).thenReturn(detail);
    }

    private static OperationContext context(ExternalOperationCallback callback) {
        return new DefaultOperationContext(RESULT_TYPE, callback, KEY, null);
    }

    private static OperationMetadata metadata(List<Class<? extends Throwable>> releaseFor) {
        return TestOperationMetadata.builder()
                .processorType(ProcessorType.NON_BLOCKING)
                .ttl(TTL)
                .lockTtl(Duration.ofMinutes(5))
                .responseCacheConfig(ResponseConfig.builder().releaseFor(releaseFor).build())
                .build();
    }

    private static NonBlockingOperationDetail detail(OperationStatus status,
                                                     boolean shouldRecordResponse,
                                                     Object result) {
        return new DefaultNonBlockingOperationDetail(true, LOCK_TOKEN, KEY, status, result, shouldRecordResponse);
    }

    private static final class RecordingCallback implements ExternalOperationCallback {

        private int calls;

        @Override
        public Object call() {
            calls++;
            return "charged";
        }
    }
}
