package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMatcher;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.InvalidFingerprintException;
import io.github.dmitriyiliyov.idempify.core.response.OperationState;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultTransactionalOperationManagerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = TestClock.EPOCH;
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(24));

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

    @Mock
    OperationStateChannel channel;

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
                null, repository, fingerprintMatcher, resultSerializer, resultDeserializer, channel, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, null, fingerprintMatcher, resultSerializer, resultDeserializer, channel, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintMatcher is null should throw NullPointerException")
    void constructor_whenFingerprintMatcherIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, null, resultSerializer, resultDeserializer, channel, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintMatcher cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, null, resultDeserializer, channel, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, null, channel, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when channel is null should throw NullPointerException")
    void constructor_whenChannelIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, resultDeserializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, resultDeserializer, channel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT startOrReply() when the key is seen for the first time should have nothing to reply with")
    void startOrReply_whenKeyIsSeenForFirstTime_shouldHaveNothingToReplyWith() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation inserted = operation(OperationStatus.IN_PROCESS, true, null);

        givenMapped(metadata, "fingerprint", inserted);
        when(repository.saveIfAbsent(inserted)).thenReturn(inserted);

        // when
        Optional<String> result = tested.startOrReply(context, metadata);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(resultDeserializer, channel);
    }

    @Test
    @DisplayName("UT startOrReply() when the operation is already processed should reply with its deserialized result")
    void startOrReply_whenOperationIsAlreadyProcessed_shouldReplyWithItsDeserializedResult() {
        // given
        OperationContext<String> context = context("fingerprint");
        OperationMetadata metadata = metadata(false);
        Operation toInsert = operation(OperationStatus.IN_PROCESS, true, null);
        Operation stored = operation(OperationStatus.PROCESSED, false, "raw");

        givenMapped(metadata, "fingerprint", toInsert);
        when(repository.saveIfAbsent(toInsert)).thenReturn(stored);
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        Optional<String> result = tested.startOrReply(context, metadata);

        // then
        assertThat(result).contains("deserialized");
    }

    @Test
    @DisplayName("UT startOrReply() when the operation is replayed should publish a replayed state carrying its expiry")
    void startOrReply_whenOperationIsReplayed_shouldPublishReplayedStateCarryingItsExpiry() {
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
        ArgumentCaptor<OperationState> captor = ArgumentCaptor.forClass(OperationState.class);
        verify(channel, times(1)).publish(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(captor.getValue().replayed()).isTrue();
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
        Optional<String> result = tested.startOrReply(context, metadata);

        // then
        assertThat(result).isEmpty();
        verify(repository, times(1)).update(toInsert, OperationStatus.PROCESSED);
        verifyNoInteractions(resultDeserializer, channel);
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
        when(repository.saveResultAndUpdateStatus("raw", OperationStatus.PROCESSED, KEY, OperationStatus.IN_PROCESS))
                .thenReturn(operation(OperationStatus.PROCESSED, true, "raw"));

        // when
        String result = tested.complete(KEY, "result");

        // then
        assertThat(result).isEqualTo("result");
        verify(repository, times(1))
                .saveResultAndUpdateStatus("raw", OperationStatus.PROCESSED, KEY, OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("UT complete() should publish a freshly executed state carrying the stored expiry")
    void complete_shouldPublishFreshlyExecutedStateCarryingStoredExpiry() {
        // given
        when(resultSerializer.serialize("result")).thenReturn("raw");
        when(repository.saveResultAndUpdateStatus("raw", OperationStatus.PROCESSED, KEY, OperationStatus.IN_PROCESS))
                .thenReturn(operation(OperationStatus.PROCESSED, true, "raw"));

        // when
        tested.complete(KEY, "result");

        // then
        ArgumentCaptor<OperationState> captor = ArgumentCaptor.forClass(OperationState.class);
        verify(channel, times(1)).publish(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(captor.getValue().replayed()).isFalse();
    }

    private DefaultTransactionalOperationManager manager(Clock clock) {
        return new DefaultTransactionalOperationManager(
                mapper, repository, fingerprintMatcher, resultSerializer, resultDeserializer, channel, clock);
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
        return new Operation(KEY, status, firstAttempt, result, "fingerprint", EXPIRES_AT, NOW);
    }
}
