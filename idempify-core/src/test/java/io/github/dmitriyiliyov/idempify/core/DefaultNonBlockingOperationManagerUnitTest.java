package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandlerResultException;
import io.github.dmitriyiliyov.idempify.core.conflict.DefaultConflictContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Written from what the branch has settled so far: a claim is a lease under a token of its own, which the
 * store counts from the call site's lock ttl; every way out of it goes through that token; a call that met another's claim gets
 * the conflict handler's answer, and that answer has to be a completed operation.
 */
@ExtendWith(MockitoExtension.class)
class DefaultNonBlockingOperationManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final Instant NOW = TestClock.EPOCH;
    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    @Mock
    private NonBlockingOperationRepository repository;
    @Mock
    private LockTokenGenerator lockTokenGenerator;
    @Mock
    private FingerprintMatcher fingerprintMatcher;
    @Mock
    private IdempotencyEventListener eventListener;
    @Mock
    private ConflictHandler conflictHandler;

    private final OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> serializer = new RowSerializer();
    private final OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserializer =
            new RowDeserializer();
    private final ResultSerializer resultSerializer = result -> "raw-" + result;

    private DefaultNonBlockingOperationManager tested;

    @BeforeEach
    void setUp() {
        tested = new DefaultNonBlockingOperationManager(
                repository,
                lockTokenGenerator,
                serializer,
                deserializer,
                fingerprintMatcher,
                resultSerializer,
                eventListener
        );
    }

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(null, lockTokenGenerator, serializer,
                deserializer, fingerprintMatcher, resultSerializer, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when lockTokenGenerator is null should throw NullPointerException")
    void constructor_whenLockTokenGeneratorIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, null, serializer,
                deserializer, fingerprintMatcher, resultSerializer, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("lockTokenGenerator cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when serializer is null should throw NullPointerException")
    void constructor_whenSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, lockTokenGenerator, null,
                deserializer, fingerprintMatcher, resultSerializer, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("serializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when deserializer is null should throw NullPointerException")
    void constructor_whenDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, lockTokenGenerator, serializer,
                null, fingerprintMatcher, resultSerializer, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, lockTokenGenerator, serializer,
                deserializer, null, resultSerializer, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, lockTokenGenerator, serializer,
                deserializer, fingerprintMatcher, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultNonBlockingOperationManager(repository, lockTokenGenerator, serializer,
                deserializer, fingerprintMatcher, resultSerializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventListener cannot be null");
    }


    @Test
    @DisplayName("UT tryLock() should claim the key under a fresh token and hand the store the lock ttl to count the lease from")
    void tryLock_shouldClaimKeyUnderFreshTokenAndHandStoreLockTtlToCountLeaseFrom() {
        // given
        when(lockTokenGenerator.generate()).thenReturn(LOCK_TOKEN);
        when(repository.lockOrSaveAndLock(any(), anyLong())).thenReturn(true);

        // when
        LockDetail result = tested.tryLock(context("fingerprint"), metadata());

        // then
        assertThat(result.isAcquired()).isTrue();
        assertThat(result.getLockToken()).isEqualTo(LOCK_TOKEN);
        ArgumentCaptor<RawNonBlockingOperation> claimed = ArgumentCaptor.forClass(RawNonBlockingOperation.class);
        verify(repository).lockOrSaveAndLock(claimed.capture(), eq(LOCK_TTL.toMillis()));
        assertThat(claimed.getValue().status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(claimed.getValue().lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(claimed.getValue().remainingLockMillis()).isNull();
        assertThat(claimed.getValue().remainingMillis()).isNull();
        assertThat(claimed.getValue().createdAt()).isNull();
    }

    @Test
    @DisplayName("UT fetchAndValidate() when this call holds the claim on a key never completed should hand back an operation to run")
    void fetchAndValidate_whenThisCallHoldsClaimOnKeyNeverCompleted_shouldHandBackOperationToRun() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.IN_PROCESS, null, null)));

        // when
        NonBlockingOperationDetail result = tested.fetchAndValidate(context("fingerprint"), metadata(), acquired());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isAcquired()).isTrue();
        assertThat(result.getLockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.shouldRecordResponse()).isTrue();
        verifyNoInteractions(conflictHandler);
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the record of a claim this call holds is gone should throw OperationDisappearedException")
    void fetchAndValidate_whenRecordOfClaimThisCallHoldsIsGone_shouldThrowOperationDisappearedException() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.empty());
        OperationContext context = context("fingerprint");
        OperationMetadata metadata = metadata();

        // when / then
        assertThatThrownBy(() -> tested.fetchAndValidate(context, metadata, acquired()))
                .isInstanceOf(OperationDisappearedException.class)
                .hasMessageContaining(KEY.toString());
        verifyNoInteractions(conflictHandler);
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the key already completed should hand back its stored result as a replay")
    void fetchAndValidate_whenKeyAlreadyCompleted_shouldHandBackItsStoredResultAsReplay() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.PROCESSED, "result", 60_000L)));

        // when
        NonBlockingOperationDetail result = tested.fetchAndValidate(context("fingerprint"), metadata(), acquired());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("result");
        assertThat(result.shouldRecordResponse()).isFalse();
        verify(repository, never()).updateByLockToken(any(), any());
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the key kept a failure should hand it back as a replay")
    void fetchAndValidate_whenKeyKeptFailure_shouldHandItBackAsReplay() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.EXCEPTIONALLY_PROCESSED, "failure", 60_000L)));

        // when
        NonBlockingOperationDetail result = tested.fetchAndValidate(context("fingerprint"), metadata(), acquired());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
        assertThat(result.shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the completed row has expired should reclaim it under this call's token")
    void fetchAndValidate_whenCompletedRowHasExpired_shouldReclaimItUnderThisCallsToken() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.PROCESSED, "stale", -1L)));
        when(repository.updateByLockToken(any(), eq(OperationStatus.PROCESSED_STATUSES)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        NonBlockingOperationDetail result = tested.fetchAndValidate(context("fingerprint"), metadata(), acquired());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.shouldRecordResponse()).isTrue();
        ArgumentCaptor<RawNonBlockingOperation> reclaimed = ArgumentCaptor.forClass(RawNonBlockingOperation.class);
        verify(repository).updateByLockToken(reclaimed.capture(), eq(OperationStatus.PROCESSED_STATUSES));
        assertThat(reclaimed.getValue().lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(reclaimed.getValue().result()).isNull();
    }

    @Test
    @DisplayName("UT fetchAndValidate() when fingerprinting is on should compare the stored fingerprint against the current one")
    void fetchAndValidate_whenFingerprintingIsOn_shouldCompareStoredFingerprintAgainstCurrentOne() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.PROCESSED, "result", 60_000L)));

        // when
        tested.fetchAndValidate(context("current"), metadata(policy), acquired());

        // then
        verify(fingerprintMatcher).match("current", "stored", policy, KEY);
    }

    @Test
    @DisplayName("UT fetchAndValidate() when fingerprinting is on and the call carries none should throw InvalidFingerprintException")
    void fetchAndValidate_whenFingerprintingIsOnAndCallCarriesNone_shouldThrowInvalidFingerprintException() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.PROCESSED, "result", 60_000L)));
        OperationContext context = context(null);
        OperationMetadata metadata = metadata(mock(FingerprintPolicy.class));

        // when / then
        assertThatThrownBy(() -> tested.fetchAndValidate(context, metadata, acquired()))
                .isInstanceOf(InvalidFingerprintException.class)
                .hasMessageContaining(KEY.toString());
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT fetchAndValidate() when fingerprinting is off should not compare anything")
    void fetchAndValidate_whenFingerprintingIsOff_shouldNotCompareAnything() {
        // given
        when(repository.find(KEY)).thenReturn(Optional.of(row(OperationStatus.PROCESSED, "result", 60_000L)));

        // when
        tested.fetchAndValidate(context("current"), metadata(), acquired());

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT fetchAndValidate() when another call holds the claim should hand back what the conflict handler answered")
    void fetchAndValidate_whenAnotherCallHoldsClaim_shouldHandBackWhatConflictHandlerAnswered() {
        // given
        when(conflictHandler.handle(new DefaultConflictContext(KEY, RESULT_TYPE)))
                .thenReturn(operation(OperationStatus.PROCESSED, "result"));

        // when
        NonBlockingOperationDetail result = tested.fetchAndValidate(context("fingerprint"), metadata(), missed());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("result");
        assertThat(result.shouldRecordResponse()).isFalse();
        assertThat(result.isAcquired()).isFalse();
        verify(eventListener).onConflict();
        verify(repository, never()).find(any());
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the conflict handler answers null should throw ConflictHandlerResultException")
    void fetchAndValidate_whenConflictHandlerAnswersNull_shouldThrowConflictHandlerResultException() {
        // given
        when(conflictHandler.handle(any())).thenReturn(null);
        OperationContext context = context("fingerprint");
        OperationMetadata metadata = metadata();

        // when / then
        assertThatThrownBy(() -> tested.fetchAndValidate(context, metadata, missed()))
                .isInstanceOf(ConflictHandlerResultException.class)
                .hasMessageContaining("returned null")
                .hasMessageContaining(KEY.toString());
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the conflict handler answers an unfinished operation should throw ConflictHandlerResultException")
    void fetchAndValidate_whenConflictHandlerAnswersUnfinishedOperation_shouldThrowConflictHandlerResultException() {
        // given
        when(conflictHandler.handle(any())).thenReturn(operation(OperationStatus.IN_PROCESS, null));
        OperationContext context = context("fingerprint");
        OperationMetadata metadata = metadata();

        // when / then
        assertThatThrownBy(() -> tested.fetchAndValidate(context, metadata, missed()))
                .isInstanceOf(ConflictHandlerResultException.class)
                .hasMessageContaining("IN_PROCESS status")
                .hasMessageContaining(KEY.toString());
    }

    @Test
    @DisplayName("UT fetchAndValidate() when the conflict handler refuses the call should let the refusal out")
    void fetchAndValidate_whenConflictHandlerRefusesCall_shouldLetRefusalOut() {
        // given
        IllegalStateException refusal = new IllegalStateException("already in progress");
        when(conflictHandler.handle(any())).thenThrow(refusal);
        OperationContext context = context("fingerprint");
        OperationMetadata metadata = metadata();

        // when / then
        assertThatThrownBy(() -> tested.fetchAndValidate(context, metadata, missed())).isSameAs(refusal);
    }

    @Test
    @DisplayName("UT complete() should store the serialized result under this call's token on IN_PROCESS")
    void complete_shouldStoreSerializedResultUnderThisCallsTokenOnInProcess() {
        // when
        NonBlockingOperationDetail result = tested.complete(running(), "result", TTL);

        // then
        verify(repository).saveResultAndUpdateStatusByLockToken(
                KEY, "raw-result", OperationStatus.PROCESSED, TTL.toMillis(), LOCK_TOKEN, OperationStatus.IN_PROCESS);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("result");
        assertThat(result.shouldRecordResponse()).isTrue();
        assertThat(result.getLockToken()).isEqualTo(LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT complete() when the operation returned null should store and hand back that null")
    void complete_whenOperationReturnedNull_shouldStoreAndHandBackThatNull() {
        // when
        NonBlockingOperationDetail result = tested.complete(running(), null, TTL);

        // then
        verify(repository).saveResultAndUpdateStatusByLockToken(
                KEY, null, OperationStatus.PROCESSED, TTL.toMillis(), LOCK_TOKEN, OperationStatus.IN_PROCESS);
        assertThat(result.getResult()).isNull();
    }

    @Test
    @DisplayName("UT completeExceptionally() should keep the serialized failure on the record under this call's token")
    void completeExceptionally_shouldKeepSerializedFailureOnRecordUnderThisCallsToken() {
        // given
        IllegalStateException failure = new IllegalStateException("card declined");

        // when
        tested.completeExceptionally(context("fingerprint"), failure, TTL, LOCK_TOKEN);

        // then
        verify(repository).saveExceptionAndUpdateStatusByLockToken(
                KEY,
                "raw-" + failure,
                ResultType.ofClass(IllegalStateException.class),
                OperationStatus.EXCEPTIONALLY_PROCESSED,
                TTL.toMillis(),
                LOCK_TOKEN
        );
    }

    @Test
    @DisplayName("UT rollback() should remove the record held under this call's token")
    void rollback_shouldRemoveRecordHeldUnderThisCallsToken() {
        // when
        tested.rollback(KEY, LOCK_TOKEN);

        // then
        verify(repository).deleteByLockToken(KEY, LOCK_TOKEN);
    }

    @Test
    @DisplayName("UT unlock() should release the claim held under this call's token")
    void unlock_shouldReleaseClaimHeldUnderThisCallsToken() {
        // when
        tested.unlock(KEY, LOCK_TOKEN);

        // then
        verify(repository).unlock(KEY, LOCK_TOKEN);
    }

    private static OperationContext context(String fingerprint) {
        return new DefaultOperationContext(RESULT_TYPE, () -> "fresh", KEY, fingerprint);
    }

    private OperationMetadata metadata() {
        return metadata(null);
    }

    private OperationMetadata metadata(FingerprintPolicy policy) {
        return TestOperationMetadata.builder()
                .processorType(ProcessorType.NON_BLOCKING)
                .lockTtl(LOCK_TTL)
                .conflictHandler(conflictHandler)
                .fingerprintPolicy(policy)
                .build();
    }

    private static LockDetail acquired() {
        return new DefaultNonBlockingOperationManager.DefaultLockDetail(true, LOCK_TOKEN);
    }

    private static LockDetail missed() {
        return new DefaultNonBlockingOperationManager.DefaultLockDetail(false, LOCK_TOKEN);
    }

    private static NonBlockingOperationDetail running() {
        return new DefaultNonBlockingOperationDetail(true, LOCK_TOKEN, KEY, OperationStatus.IN_PROCESS, null, true);
    }

    private static RawNonBlockingOperation row(OperationStatus status, String result, Long remainingMillis) {
        return new RawNonBlockingOperation(KEY, status, false, result, null, "stored", remainingMillis, NOW,
                LOCK_TOKEN, LOCK_TTL.toMillis());
    }

    private static NonBlockingOperation operation(OperationStatus status, Object result) {
        return new NonBlockingOperation(KEY, status, false, result, RESULT_TYPE, null, "stored", 60_000L, NOW,
                null, null);
    }

    private static final class RowSerializer
            implements OperationSerializer<NonBlockingOperation, RawNonBlockingOperation> {

        @Override
        public RawNonBlockingOperation serialize(NonBlockingOperation operation) {
            return new RawNonBlockingOperation(
                    operation.getIdempotencyKey(),
                    operation.getStatus(),
                    operation.isFirstAttempt(),
                    (String) operation.getResult(),
                    null,
                    operation.getFingerprint(),
                    operation.getRemainingMillis(),
                    operation.getCreatedAt(),
                    operation.getLockToken(),
                    operation.getRemainingLockMillis()
            );
        }
    }

    private static final class RowDeserializer
            implements OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> {

        @Override
        public NonBlockingOperation deserialize(RawNonBlockingOperation row, ResultType resultType) {
            return new NonBlockingOperation(
                    row.idempotencyKey(),
                    row.status(),
                    row.isFirstAttempt(),
                    row.result(),
                    resultType,
                    null,
                    row.fingerprint(),
                    row.remainingMillis(),
                    row.createdAt(),
                    row.lockToken(),
                    row.remainingLockMillis()
            );
        }
    }
}
