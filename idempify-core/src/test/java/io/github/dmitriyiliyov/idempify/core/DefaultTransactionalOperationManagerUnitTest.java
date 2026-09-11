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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The manager stands between a store that deals in rows and a caller that deals in operations, so the cases
 * below are about what it does with a row it got back, not about the translation itself: the serializer and
 * the deserializer are honest round-tripping stubs, and what is judged is which row went down and what the
 * caller was told.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTransactionalOperationManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final Instant NOW = TestClock.EPOCH;
    private static final Duration TTL = Duration.ofHours(24);
    private static final Instant EXPIRES_AT = NOW.plus(TTL);

    @Mock
    private OperationCreator creator;
    @Mock
    private TransactionalOperationRepository repository;
    @Mock
    private FingerprintMatcher fingerprintMatcher;

    private final OperationSerializer serializer = new RowSerializer();
    private final OperationDeserializer deserializer = new RowDeserializer();
    private final ResultSerializer resultSerializer = new TextResultSerializer();

    private TestClock clock;
    private DefaultTransactionalOperationManager tested;

    @BeforeEach
    void setUp() {
        clock = TestClock.fixedAt(NOW);
        tested = manager(clock);
    }

    @Test
    @DisplayName("UT constructor() when creator is null should throw NullPointerException")
    void constructor_whenCreatorIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                null, repository, fingerprintMatcher, serializer, deserializer, resultSerializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("creator cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, null, fingerprintMatcher, serializer, deserializer, resultSerializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, repository, null, serializer, deserializer, resultSerializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when serializer is null should throw NullPointerException")
    void constructor_whenSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, repository, fingerprintMatcher, null, deserializer, resultSerializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("serializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when deserializer is null should throw NullPointerException")
    void constructor_whenDeserializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, repository, fingerprintMatcher, serializer, null, resultSerializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("deserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, repository, fingerprintMatcher, serializer, deserializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                creator, repository, fingerprintMatcher, serializer, deserializer, resultSerializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    @Test
    @DisplayName("UT startOrReply() when the key is seen for the first time should hand back an operation nobody has completed")
    void startOrReply_whenKeyIsSeenForFirstTime_shouldHandBackOperationNobodyHasCompleted() {
        // given
        givenClaimed(claim());

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.getResult()).isNull();
        assertThat(detail.replayed()).isFalse();
    }

    @Test
    @DisplayName("UT startOrReply() should build the claim from the clock's own instant")
    void startOrReply_shouldBuildClaimFromClocksOwnInstant() {
        // given
        OperationContext context = context("fingerprint");
        givenClaimed(claim());

        // when
        tested.startOrReply(context, metadata(false));

        // then
        verify(creator).create(context, NOW);
    }

    @Test
    @DisplayName("UT startOrReply() should claim the key with a row that carries no expiry")
    void startOrReply_shouldClaimKeyWithRowThatCarriesNoExpiry() {
        // given
        givenClaimed(claim());

        // when
        tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        ArgumentCaptor<RawOperation> claimed = ArgumentCaptor.forClass(RawOperation.class);
        verify(repository).saveIfAbsent(claimed.capture());
        assertThat(claimed.getValue().expiresAt()).isNull();
        assertThat(claimed.getValue().status()).isEqualTo(OperationStatus.IN_PROCESS);
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
        assertThat(detail.replayed()).isTrue();
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
        assertThat(detail.replayed()).isTrue();
        assertThat(detail.getResult()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row has expired should rewrite it and start over")
    void startOrReply_whenStoredRowHasExpired_shouldRewriteItAndStartOver() {
        // given
        RawOperation claim = row(claim());
        when(creator.create(any(), eq(NOW))).thenReturn(claim());
        when(repository.saveIfAbsent(claim)).thenReturn(row(expired()));
        when(repository.update(claim, OperationStatus.PROCESSED)).thenReturn(claim);

        // when
        OperationDetail detail = tested.startOrReply(context("fingerprint"), metadata(false));

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.replayed()).isFalse();
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
    @DisplayName("UT startOrReply() when the stored row is in process past its expiry should leave it alone")
    void startOrReply_whenStoredRowIsInProcessPastItsExpiry_shouldLeaveItAlone() {
        // given
        Operation stale = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null,
                "fingerprint", NOW.minusSeconds(1), NOW.minusSeconds(10));
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
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null,
                "stored", null, NOW);
        givenClaimAnsweredWith(stored);

        // when
        tested.startOrReply(context("current"), metadata(policy));

        // then
        verify(fingerprintMatcher, times(1)).match("current", "stored", policy, KEY);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key repeats without a fingerprint should throw InvalidFingerprintException")
    void startOrReply_whenFingerprintingIsOnAndKeyRepeatsWithoutFingerprint_shouldThrowInvalidFingerprintException() {
        // given
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null,
                "stored", null, NOW);
        givenClaimAnsweredWith(stored);
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
        givenClaimed(claim());

        // when
        tested.startOrReply(context("current"), metadata(true));

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is off should not compare anything")
    void startOrReply_whenFingerprintingIsOff_shouldNotCompareAnything() {
        // given
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, RESULT_TYPE, null,
                "stored", null, NOW);
        givenClaimAnsweredWith(stored);

        // when
        tested.startOrReply(context("current"), metadata(false));

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT complete() should store the serialized result under a compare-and-swap on IN_PROCESS")
    void complete_shouldStoreSerializedResultUnderCompareAndSwapOnInProcess() {
        // given
        givenCompletionReturns(processed("result"));

        // when
        OperationDetail detail = tested.complete(KEY, "result", RESULT_TYPE, TTL);

        // then
        assertThat(detail.getResult()).isEqualTo("result");
        verify(repository, times(1)).saveResultAndUpdateStatus(
                KEY, "raw-result", OperationStatus.PROCESSED, EXPIRES_AT, OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("UT complete() should count the expiry from the moment the result exists, not from the claim")
    void complete_shouldCountExpiryFromMomentResultExistsNotFromClaim() {
        // given
        clock.advance(Duration.ofHours(2));
        givenCompletionReturns(processed("result"));

        // when
        tested.complete(KEY, "result", RESULT_TYPE, TTL);

        // then
        assertThat(capturedExpiry()).isEqualTo(NOW.plus(Duration.ofHours(2)).plus(TTL));
    }

    @Test
    @DisplayName("UT complete() when the operation outran its own ttl should still store an expiry in the future")
    void complete_whenOperationOutranItsOwnTtl_shouldStillStoreExpiryInTheFuture() {
        // given
        clock.advance(TTL.plus(Duration.ofMinutes(1)));
        givenCompletionReturns(processed("result"));

        // when
        tested.complete(KEY, "result", RESULT_TYPE, TTL);

        // then
        assertThat(capturedExpiry()).isAfter(clock.instant());
    }

    @Test
    @DisplayName("UT complete() should hand back a detail that is not a replay")
    void complete_shouldHandBackDetailThatIsNotReplay() {
        // given
        givenCompletionReturns(processed("result"));

        // when
        OperationDetail detail = tested.complete(KEY, "result", RESULT_TYPE, TTL);

        // then
        assertThat(detail.replayed()).isFalse();
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT complete() should hand back the result the store recorded rather than the one it was given")
    void complete_shouldHandBackResultStoreRecordedRatherThanOneItWasGiven() {
        // given
        givenCompletionReturns(processed("recorded"));

        // when
        OperationDetail detail = tested.complete(KEY, "result", RESULT_TYPE, TTL);

        // then
        assertThat(detail.getResult()).isEqualTo("recorded");
    }

    @Test
    @DisplayName("UT complete() when the operation returned null should store and hand back that null")
    void complete_whenOperationReturnedNull_shouldStoreAndHandBackThatNull() {
        // given
        givenCompletionReturns(processed(null));

        // when
        OperationDetail detail = tested.complete(KEY, null, RESULT_TYPE, TTL);

        // then
        assertThat(detail.getResult()).isNull();
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        verify(repository).saveResultAndUpdateStatus(
                KEY, null, OperationStatus.PROCESSED, EXPIRES_AT, OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("UT complete() when the row is no longer in process should let the mismatch out")
    void complete_whenRowIsNoLongerInProcess_shouldLetMismatchOut() {
        // given
        when(repository.saveResultAndUpdateStatus(any(), any(), any(), any(), any()))
                .thenThrow(new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS));

        // when / then
        assertThatThrownBy(() -> tested.complete(KEY, "result", RESULT_TYPE, TTL))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(KEY.toString());
    }

    private void givenClaimed(Operation claim) {
        when(creator.create(any(), eq(NOW))).thenReturn(claim);
        when(repository.saveIfAbsent(row(claim))).thenReturn(row(claim));
    }

    private void givenClaimAnsweredWith(Operation stored) {
        when(creator.create(any(), eq(NOW))).thenReturn(claim());
        when(repository.saveIfAbsent(row(claim()))).thenReturn(row(stored));
    }

    private void givenCompletionReturns(Operation completed) {
        when(repository.saveResultAndUpdateStatus(eq(KEY), any(), eq(OperationStatus.PROCESSED), any(),
                eq(OperationStatus.IN_PROCESS)))
                .thenReturn(row(completed));
    }

    private Instant capturedExpiry() {
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).saveResultAndUpdateStatus(any(), any(), any(), expiresAt.capture(), any());
        return expiresAt.getValue();
    }

    private DefaultTransactionalOperationManager manager(Clock clock) {
        return new DefaultTransactionalOperationManager(
                creator, repository, fingerprintMatcher, serializer, deserializer, resultSerializer, clock);
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

    private static Operation processed(String result) {
        return new Operation(KEY, OperationStatus.PROCESSED, false, result, RESULT_TYPE, null, "fingerprint",
                EXPIRES_AT, NOW);
    }

    private static Operation expired() {
        return new Operation(KEY, OperationStatus.PROCESSED, false, "stale", RESULT_TYPE, null, "old",
                NOW.minusSeconds(1), NOW.minusSeconds(10));
    }

    private static RawOperation row(Operation operation) {
        return new RowSerializer().serialize(operation);
    }

    private static final class RowSerializer implements OperationSerializer {

        @Override
        public RawOperation serialize(Operation operation) {
            return operation == null ? null : new RawOperation(
                    operation.getIdempotencyKey(),
                    operation.getStatus(),
                    operation.isFirstAttempt(),
                    (String) operation.getResult(),
                    null,
                    operation.getFingerprint(),
                    operation.getExpiresAt(),
                    operation.getCreatedAt()
            );
        }
    }

    private static final class RowDeserializer implements OperationDeserializer {

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
                    operation.expiresAt(),
                    operation.createdAt()
            );
        }
    }

    private static final class TextResultSerializer implements ResultSerializer {

        @Override
        public String serialize(Object result) {
            return result == null ? null : "raw-" + result;
        }
    }
}
