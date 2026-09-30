package io.github.dmitriyiliyov.idempify.core;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * The manager stands between a store that deals in rows and a caller that deals in operations, so the cases
 * below are about what it does with a row it got back, not about the translation itself: the serializer and
 * the deserializer are honest round-tripping stubs, and what is judged is which row went down and what the
 * caller was told.
 */
@ExtendWith(MockitoExtension.class)
class DefaultBlockingOperationManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final Instant NOW = TestClock.EPOCH;
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private BlockingOperationRepository repository;
    @Mock
    private FingerprintMatcher fingerprintMatcher;

    private final OperationSerializer<Operation, RawOperation> serializer = new RowSerializer();
    private final OperationDeserializer<RawOperation, Operation> deserializer = new RowDeserializer();
    private final ResultSerializer resultSerializer = new TextResultSerializer();

    private DefaultBlockingOperationManager tested;

    @BeforeEach
    void setUp() {
        tested = new DefaultBlockingOperationManager(
                repository, fingerprintMatcher, serializer, deserializer, resultSerializer);
    }

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultBlockingOperationManager(
                null, fingerprintMatcher, serializer, deserializer, resultSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultBlockingOperationManager(
                repository, null, serializer, deserializer, resultSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when serializer is null should throw NullPointerException")
    void constructor_whenSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultBlockingOperationManager(
                repository, fingerprintMatcher, null, deserializer, resultSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("serializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when deserializer is null should throw NullPointerException")
    void constructor_whenDeserializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultBlockingOperationManager(
                repository, fingerprintMatcher, serializer, null, resultSerializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultBlockingOperationManager(
                repository, fingerprintMatcher, serializer, deserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultSerializer cannot be null");
    }


    @Test
    @DisplayName("UT startOrReply() when the key is seen for the first time should hand back an operation nobody has completed")
    void startOrReply_whenKeyIsSeenForFirstTime_shouldHandBackOperationNobodyHasCompleted() {
        // given
        givenClaimAnsweredWith(claim());

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.getResult()).isNull();
        assertThat(detail.shouldRecordResponse()).isTrue();
    }

    @Test
    @DisplayName("UT startOrReply() should claim the key with a first-attempt row leaving creation time and expiry to the store")
    void startOrReply_shouldClaimKeyWithFirstAttemptRowLeavingCreationTimeAndExpiryToStore() {
        // given
        givenClaimAnsweredWith(claim());

        // when
        tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        ArgumentCaptor<RawOperation> claimed = ArgumentCaptor.forClass(RawOperation.class);
        verify(repository).saveIfAbsent(claimed.capture());
        assertThat(claimed.getValue().idempotencyKey()).isEqualTo(KEY);
        assertThat(claimed.getValue().status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(claimed.getValue().isFirstAttempt()).isTrue();
        assertThat(claimed.getValue().fingerprint()).isEqualTo("fingerprint");
        assertThat(claimed.getValue().createdAt()).isNull();
        assertThat(claimed.getValue().remainingMillis()).isNull();
        assertThat(claimed.getValue().result()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() when the operation already completed should hand back its stored result as a replay")
    void startOrReply_whenOperationAlreadyCompleted_shouldHandBackItsStoredResultAsReplay() {
        // given
        givenClaimAnsweredWith(processed("result"));

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(detail.getResult()).isEqualTo("result");
        assertThat(detail.shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("UT startOrReply() when the stored result is null should replay it instead of taking it for no result")
    void startOrReply_whenStoredResultIsNull_shouldReplayItInsteadOfTakingItForNoResult() {
        // given
        givenClaimAnsweredWith(processed(null));

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(detail.shouldRecordResponse()).isFalse();
        assertThat(detail.getResult()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row has expired should rewrite it with the fresh claim and start over")
    void startOrReply_whenStoredRowHasExpired_shouldRewriteItWithFreshClaimAndStartOver() {
        // given
        RawOperation claim = row(Operation.ofContext(context("fingerprint")));
        when(repository.saveIfAbsent(claim)).thenReturn(row(expired()));
        when(repository.update(claim, OperationStatus.PROCESSED)).thenReturn(claim);

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.shouldRecordResponse()).isTrue();
        assertThat(detail.getResult()).isNull();
        verify(repository, times(1)).update(claim, OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row is still live should not rewrite it")
    void startOrReply_whenStoredRowIsStillLive_shouldNotRewriteIt() {
        // given
        givenClaimAnsweredWith(processed("result"));

        // when
        tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        verify(repository, never()).update(any(), any());
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row is in process with its time spent should leave it alone")
    void startOrReply_whenStoredRowIsInProcessWithItsTimeSpent_shouldLeaveItAlone() {
        // given
        Operation stale = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null,
                "fingerprint", -1L, NOW.minusSeconds(10));
        givenClaimAnsweredWith(stale);

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        verify(repository, never()).update(any(), any());
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key repeats should compare the stored fingerprint against the current one")
    void startOrReply_whenFingerprintingIsOnAndKeyRepeats_shouldCompareStoredFingerprintAgainstCurrentOne() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        givenClaimAnsweredWith(repeated("stored"), context("current"));

        // when
        tested.startOrReply(context("current"), metadata(policy));

        // then
        verify(fingerprintMatcher, times(1)).match("current", "stored", policy, KEY);
    }

    @Test
    @DisplayName("UT startOrReply() when the fingerprints do not match should let the mismatch out")
    void startOrReply_whenFingerprintsDoNotMatch_shouldLetMismatchOut() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        givenClaimAnsweredWith(repeated("stored"), context("current"));
        IllegalStateException mismatch = new IllegalStateException("fingerprint mismatch");
        doThrow(mismatch).when(fingerprintMatcher).match("current", "stored", policy, KEY);
        OperationContext context = context("current");
        OperationMetadata metadata = metadata(policy);

        // when / then
        assertThatThrownBy(() -> tested.startOrReply(context, metadata)).isSameAs(mismatch);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key repeats without a fingerprint should throw InvalidFingerprintException")
    void startOrReply_whenFingerprintingIsOnAndKeyRepeatsWithoutFingerprint_shouldThrowInvalidFingerprintException() {
        // given
        givenClaimAnsweredWith(repeated("stored"), context(null));
        OperationContext context = context(null);
        OperationMetadata metadata = metadata(true);

        // when / then
        assertThatThrownBy(() -> tested.startOrReply(context, metadata))
                .isInstanceOf(InvalidFingerprintException.class)
                .hasMessageContaining(KEY.toString());
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key is new should not compare anything")
    void startOrReply_whenFingerprintingIsOnAndKeyIsNew_shouldNotCompareAnything() {
        // given
        givenClaimAnsweredWith(claim(), context("current"));

        // when
        tested.startOrReply(context("current"), metadata(true));

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is off should not compare anything")
    void startOrReply_whenFingerprintingIsOff_shouldNotCompareAnything() {
        // given
        givenClaimAnsweredWith(repeated("stored"), context("current"));

        // when
        tested.startOrReply(context("current"), metadata(false));

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT complete() should store the serialized result under a compare-and-swap on IN_PROCESS")
    void complete_shouldStoreSerializedResultUnderCompareAndSwapOnInProcess() {
        // when
        OperationDetail detail = tested.complete(KEY, "result", TTL);

        // then
        assertThat(detail.getResult()).isEqualTo("result");
        verify(repository, times(1)).saveResultAndUpdateStatus(
                KEY, "raw-result", OperationStatus.PROCESSED, TTL.toMillis(), OperationStatus.IN_PROCESS);
    }


    @Test
    @DisplayName("UT complete() should hand back a detail whose response is still to be recorded")
    void complete_shouldHandBackDetailWhoseResponseIsStillToBeRecorded() {
        // when
        OperationDetail detail = tested.complete(KEY, "result", TTL);

        // then
        assertThat(detail.shouldRecordResponse()).isTrue();
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT complete() should hand back the caller's own result rather than a copy read back from the store")
    void complete_shouldHandBackCallersOwnResultRatherThanCopyReadBackFromStore() {
        // given
        Object result = new Object();

        // when
        OperationDetail detail = tested.complete(KEY, result, TTL);

        // then
        assertThat(detail.getResult()).isSameAs(result);
    }

    @Test
    @DisplayName("UT complete() when the operation returned null should store and hand back that null")
    void complete_whenOperationReturnedNull_shouldStoreAndHandBackThatNull() {
        // when
        OperationDetail detail = tested.complete(KEY, null, TTL);

        // then
        assertThat(detail.getResult()).isNull();
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        verify(repository).saveResultAndUpdateStatus(
                KEY, null, OperationStatus.PROCESSED, TTL.toMillis(), OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("UT complete() when the row is no longer in process should let the mismatch out")
    void complete_whenRowIsNoLongerInProcess_shouldLetMismatchOut() {
        // given
        doThrow(new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS))
                .when(repository).saveResultAndUpdateStatus(any(), any(), any(), anyLong(), any());

        // when / then
        assertThatThrownBy(() -> tested.complete(KEY, "result", TTL))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(KEY.toString());
    }

    private void givenClaimAnsweredWith(Operation stored) {
        givenClaimAnsweredWith(stored, context("fingerprint"));
    }

    /**
     * The claim is the row the manager builds from the context itself, so the stub has to be keyed by the
     * same context the case runs with.
     */
    private void givenClaimAnsweredWith(Operation stored, OperationContext context) {
        when(repository.saveIfAbsent(row(Operation.ofContext(context)))).thenReturn(row(stored));
    }

    private static OperationContext context(String fingerprint) {
        return new DefaultOperationContext(RESULT_TYPE, () -> "fresh", KEY, fingerprint);
    }

    private static OperationMetadata metadata(boolean useFingerprint) {
        return metadata(useFingerprint ? mock(FingerprintPolicy.class) : null);
    }

    private static OperationMetadata metadata(FingerprintPolicy policy) {
        return TestOperationMetadata.builder()
                .fingerprintPolicy(policy)
                .build();
    }

    private static Operation claim() {
        return new Operation(KEY, OperationStatus.IN_PROCESS, true, null, RESULT_TYPE, null, "fingerprint",
                null, NOW);
    }

    private static Operation repeated(String fingerprint) {
        return new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null, fingerprint,
                null, NOW);
    }

    private static Operation processed(String result) {
        return new Operation(KEY, OperationStatus.PROCESSED, false, result, RESULT_TYPE, null, "fingerprint",
                TTL.toMillis(), NOW);
    }

    private static Operation expired() {
        return new Operation(KEY, OperationStatus.PROCESSED, false, "stale", RESULT_TYPE, null, "old",
                -1L, NOW.minusSeconds(10));
    }

    private static RawOperation row(Operation operation) {
        return new RowSerializer().serialize(operation);
    }

    private static final class RowSerializer implements OperationSerializer<Operation, RawOperation> {

        @Override
        public RawOperation serialize(Operation operation) {
            return operation == null ? null : new RawOperation(
                    operation.getIdempotencyKey(),
                    operation.getStatus(),
                    operation.isFirstAttempt(),
                    (String) operation.getResult(),
                    null,
                    operation.getFingerprint(),
                    operation.getRemainingMillis(),
                    operation.getCreatedAt()
            );
        }
    }

    private static final class RowDeserializer implements OperationDeserializer<RawOperation, Operation> {

        @Override
        public Operation deserialize(RawOperation operation, ResultType resultType) {
            return operation == null ? null : new Operation(
                    operation.idempotencyKey(),
                    operation.status(),
                    operation.isFirstAttempt(),
                    operation.result(),
                    resultType,
                    null,
                    operation.fingerprint(),
                    operation.remainingMillis(),
                    operation.createdAt()
            );
        }
    }

    private static final class TextResultSerializer implements ResultSerializer {

        @Override
        public String serialize(Object result) {
            return "raw-" + result;
        }
    }
}
