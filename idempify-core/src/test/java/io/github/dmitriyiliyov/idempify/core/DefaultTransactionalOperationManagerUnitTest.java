package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
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

@ExtendWith(MockitoExtension.class)
class DefaultTransactionalOperationManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = TestClock.EPOCH;
    private static final Duration TTL = Duration.ofHours(24);
    private static final Instant EXPIRES_AT = NOW.plus(TTL);

    @Mock
    OperationMapper mapper;

    @Mock
    TransactionalOperationRepository repository;

    @Mock
    FingerprintMatcher fingerprintMatcher;

    @Mock
    ResultSerializer resultSerializer;

    @Mock
    ResultDeserializer resultDeserializer;

    TestClock clock;

    DefaultTransactionalOperationManager tested;

    @BeforeEach
    void setUp() {
        clock = TestClock.fixedAt(NOW);
        tested = manager(clock);
    }

    @Test
    @DisplayName("UT constructor when mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                null, repository, fingerprintMatcher, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, null, fingerprintMatcher, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, null, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, null, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, resultDeserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT startOrReply() when the key is seen for the first time should hand back an operation nobody has completed")
    void startOrReply_whenKeyIsSeenForFirstTime_shouldHandBackOperationNobodyHasCompleted() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation inserted = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, "fingerprint", inserted);
        when(repository.saveIfAbsent(inserted)).thenReturn(inserted);

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.getResult()).isNull();
        assertThat(detail.replayed()).isFalse();
        verifyNoInteractions(resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when the key is claimed should hand back a detail with no expiry yet")
    void startOrReply_whenKeyIsClaimed_shouldHandBackDetailWithNoExpiryYet() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation claimed = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, "fingerprint", claimed);
        when(repository.saveIfAbsent(claimed)).thenReturn(claimed);

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() should map the operation without an expiry of its own")
    void startOrReply_shouldMapOperationWithoutExpiryOfItsOwn() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation claimed = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, "fingerprint", claimed);
        when(repository.saveIfAbsent(claimed)).thenReturn(claimed);

        // when
        tested.startOrReply(context, metadata);

        // then
        ArgumentCaptor<Operation> saved = ArgumentCaptor.forClass(Operation.class);
        verify(repository).saveIfAbsent(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() when the operation is already processed should hand back its deserialized result")
    void startOrReply_whenOperationIsAlreadyProcessed_shouldHandBackItsDeserializedResult() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = operation(OperationStatus.PROCESSED, false, "raw");

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(detail.getResult()).isEqualTo("deserialized");
    }

    @Test
    @DisplayName("UT startOrReply() when the operation is replayed should mark the detail replayed and carry the stored expiry")
    void startOrReply_whenOperationIsReplayed_shouldMarkDetailReplayedAndCarryStoredExpiry() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = operation(OperationStatus.PROCESSED, false, "raw");

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.replayed()).isTrue();
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("UT startOrReply() when the stored result is null should replay it instead of taking it for no result")
    void startOrReply_whenStoredResultIsNull_shouldReplayItInsteadOfTakingItForNoResult() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = operation(OperationStatus.PROCESSED, false, null);

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);
        when(resultDeserializer.deserialize(null, String.class)).thenReturn(null);

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(detail.replayed()).isTrue();
        assertThat(detail.getResult()).isNull();
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row has expired should rewrite it and start over")
    void startOrReply_whenStoredRowHasExpired_shouldRewriteItAndStartOver() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation expired = new Operation(KEY, OperationStatus.PROCESSED, false, "stale", "old", NOW.minusSeconds(1), NOW.minusSeconds(10));

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(expired);
        when(repository.update(toInsert, OperationStatus.PROCESSED)).thenReturn(toInsert);

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.replayed()).isFalse();
        verify(repository, times(1)).update(toInsert, OperationStatus.PROCESSED);
        verifyNoInteractions(resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row is still live should not rewrite it")
    void startOrReply_whenStoredRowIsStillLive_shouldNotRewriteIt() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = operation(OperationStatus.PROCESSED, false, "raw");

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        tested.startOrReply(context, metadata);

        // then
        verify(repository, never()).update(any(), any());
    }

    @Test
    @DisplayName("UT startOrReply() when the stored row is in process past its expiry should leave it alone")
    void startOrReply_whenStoredRowIsInProcessPastItsExpiry_shouldLeaveItAlone() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stale = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, "fingerprint", NOW.minusSeconds(1), NOW.minusSeconds(10));

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stale);

        // when
        OperationDetail<String> detail = tested.startOrReply(context, metadata);

        // then
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(detail.getExpiresAt()).isEqualTo(NOW.minusSeconds(1));
        verify(repository, never()).update(any(), any());
    }

    @Test
    @DisplayName("UT startOrReply() when the context carries no fingerprint should map the operation without one")
    void startOrReply_whenContextCarriesNoFingerprint_shouldMapOperationWithoutOne() {
        // given
        OperationContext<String> context = context(null);
        OperationMetadata metadata = metadata(false);
        Operation inserted = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, null, inserted);
        when(repository.saveIfAbsent(inserted)).thenReturn(inserted);

        // when
        tested.startOrReply(context, metadata);

        // then
        verify(mapper, times(1)).toOperation(KEY, null, metadata, NOW);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key repeats should compare the stored fingerprint against the current one")
    void startOrReply_whenFingerprintingIsOnAndKeyRepeats_shouldCompareStoredFingerprintAgainstCurrentOne() {
        // given
        FingerprintPolicy policy = mock(FingerprintPolicy.class);
        OperationContext<String> context = context("current");
        OperationMetadata metadata = metadata(policy);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, "stored", EXPIRES_AT, NOW);

        givenMapped(metadata, "current", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);

        // when
        tested.startOrReply(context, metadata);

        // then
        verify(fingerprintMatcher, times(1)).match("current", "stored", policy, KEY);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is on and the key repeats without a fingerprint should throw InvalidFingerprintException")
    void startOrReply_whenFingerprintingIsOnAndKeyRepeatsWithoutFingerprint_shouldThrowInvalidFingerprintException() {
        // given
        OperationContext<String> context = context(null);
        OperationMetadata metadata = metadata(true);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, "stored", EXPIRES_AT, NOW);

        givenMapped(metadata, null, toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);

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
        OperationContext<String> context = context("current");
        OperationMetadata metadata = metadata(true);
        Operation inserted = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, "current", inserted);
        when(repository.saveIfAbsent(inserted)).thenReturn(inserted);

        // when
        tested.startOrReply(context, metadata);

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT startOrReply() when fingerprinting is off should not compare anything")
    void startOrReply_whenFingerprintingIsOff_shouldNotCompareAnything() {
        // given
        OperationContext<String> context = context("current");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = new Operation(KEY, OperationStatus.IN_PROCESS, false, null, "stored", EXPIRES_AT, NOW);

        givenMapped(metadata, "current", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);

        // when
        tested.startOrReply(context, metadata);

        // then
        verifyNoInteractions(fingerprintMatcher);
    }

    @Test
    @DisplayName("UT complete() should store the serialized result under a compare-and-swap on IN_PROCESS")
    void complete_shouldStoreSerializedResultUnderCompareAndSwapOnInProcess() {
        // given
        when(resultSerializer.serialize("result")).thenReturn("raw");
        givenCompletionReturns(completedAt("raw", EXPIRES_AT));

        // when
        OperationDetail<String> detail = tested.complete(KEY, TTL, "result");

        // then
        assertThat(detail.getResult()).isEqualTo("result");
        verify(repository, times(1))
                .saveResultAndUpdateStatus(eq("raw"), eq(OperationStatus.PROCESSED), any(),
                        eq(KEY), eq(OperationStatus.IN_PROCESS));
    }

    @Test
    @DisplayName("UT complete() should count the expiry from the moment the result exists, not from the claim")
    void complete_shouldCountExpiryFromMomentResultExistsNotFromClaim() {
        // given
        clock.advance(Duration.ofHours(2));
        when(resultSerializer.serialize("result")).thenReturn("raw");
        givenCompletionReturns(completedAt("raw", EXPIRES_AT));

        // when
        tested.complete(KEY, TTL, "result");

        // then
        assertThat(capturedExpiry()).isEqualTo(NOW.plus(Duration.ofHours(2)).plus(TTL));
    }

    @Test
    @DisplayName("UT complete() when the operation outran its own ttl should still store an expiry in the future")
    void complete_whenOperationOutranItsOwnTtl_shouldStillStoreExpiryInTheFuture() {
        // given
        clock.advance(TTL.plus(Duration.ofMinutes(1)));
        when(resultSerializer.serialize("result")).thenReturn("raw");
        givenCompletionReturns(completedAt("raw", EXPIRES_AT));

        // when
        tested.complete(KEY, TTL, "result");

        // then
        assertThat(capturedExpiry()).isAfter(clock.instant());
    }

    @Test
    @DisplayName("UT complete() should hand back the expiry the store settled on rather than the one it asked for")
    void complete_shouldHandBackExpiryStoreSettledOnRatherThanOneItAskedFor() {
        // given
        Instant storeSettledOn = NOW.plus(Duration.ofMinutes(5));
        when(resultSerializer.serialize("result")).thenReturn("raw");
        givenCompletionReturns(completedAt("raw", storeSettledOn));

        // when
        OperationDetail<String> detail = tested.complete(KEY, TTL, "result");

        // then
        assertThat(detail.getExpiresAt()).isEqualTo(storeSettledOn);
        assertThat(capturedExpiry()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("UT complete() should hand back a detail that is not a replay")
    void complete_shouldHandBackDetailThatIsNotReplay() {
        // given
        when(resultSerializer.serialize("result")).thenReturn("raw");
        givenCompletionReturns(completedAt("raw", EXPIRES_AT));

        // when
        OperationDetail<String> detail = tested.complete(KEY, TTL, "result");

        // then
        assertThat(detail.replayed()).isFalse();
        assertThat(detail.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT complete() when the operation returned null should store and hand back that null")
    void complete_whenOperationReturnedNull_shouldStoreAndHandBackThatNull() {
        // given
        when(resultSerializer.serialize(null)).thenReturn(null);
        givenCompletionReturns(completedAt(null, EXPIRES_AT));

        // when
        OperationDetail<String> detail = tested.complete(KEY, TTL, null);

        // then
        assertThat(detail.getResult()).isNull();
        assertThat(detail.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT complete() when the row is no longer in process should let the mismatch out")
    void complete_whenRowIsNoLongerInProcess_shouldLetMismatchOut() {
        // given
        when(resultSerializer.serialize("result")).thenReturn("raw");
        when(repository.saveResultAndUpdateStatus(any(), any(), any(), any(), any()))
                .thenThrow(new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS));

        // when / then
        assertThatThrownBy(() -> tested.complete(KEY, TTL, "result"))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(KEY.toString());
    }

    private void givenCompletionReturns(Operation completed) {
        when(repository.saveResultAndUpdateStatus(any(), eq(OperationStatus.PROCESSED), any(),
                eq(KEY), eq(OperationStatus.IN_PROCESS)))
                .thenReturn(completed);
    }

    private Instant capturedExpiry() {
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).saveResultAndUpdateStatus(any(), any(), expiresAt.capture(), any(), any());
        return expiresAt.getValue();
    }

    private DefaultTransactionalOperationManager manager(Clock clock) {
        return new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, resultDeserializer, clock);
    }

    private void givenMapped(OperationMetadata metadata, String fingerprint, Operation mapped) {
        when(mapper.toOperation(KEY, fingerprint, metadata, NOW)).thenReturn(mapped);
    }

    private OperationContext<String> context(String fingerprint) {
        return new DefaultOperationContext<>(String.class, () -> "fresh", KEY, fingerprint);
    }

    private OperationMetadata metadata(boolean useFingerprint) {
        return metadata(useFingerprint ? mock(FingerprintPolicy.class) : null);
    }

    private OperationMetadata metadata(FingerprintPolicy policy) {
        return TestOperationMetadata.builder()
                .fingerprintPolicy(policy)
                .build();
    }

    private Operation operation(OperationStatus status, boolean firstAttempt, String result) {
        Instant expiresAt = OperationStatus.PROCESSED.equals(status) ? EXPIRES_AT : null;
        return new Operation(KEY, status, firstAttempt, result, "fingerprint", expiresAt, NOW);
    }

    private Operation completedAt(String result, Instant expiresAt) {
        return new Operation(KEY, OperationStatus.PROCESSED, true, result, "fingerprint", expiresAt, NOW);
    }
}
