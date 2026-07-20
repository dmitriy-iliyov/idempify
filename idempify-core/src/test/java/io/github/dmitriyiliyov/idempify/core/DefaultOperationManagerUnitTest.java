package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.DelegatingConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.DefaultFingerprintMismatchContext;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultOperationManagerUnitTest {

    @Mock
    OperationMapper mapper;

    @Mock
    OperationRepository repository;

    @Mock
    IdempotencyEventListener eventListener;

    @Mock
    DelegatingConflictHandler conflictHandler;

    @Mock
    FingerprintManager fingerprintManager;

    @Mock
    ResultSerializer resultSerializer;

    @Mock
    ResultDeserializer resultDeserializer;

    @Mock
    Clock clock;

    @InjectMocks
    DefaultOperationManager tested;

    @Test
    @DisplayName("UT constructor when mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(null, repository, eventListener, conflictHandler, fingerprintManager, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, null, eventListener, conflictHandler, fingerprintManager, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, null, conflictHandler, fingerprintManager, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT constructor when conflictHandler is null should throw NullPointerException")
    void constructor_whenConflictHandlerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, null, fingerprintManager, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandlerClass cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintManager is null should throw NullPointerException")
    void constructor_whenFingerprintManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, null, resultSerializer, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultSerializer is null should throw NullPointerException")
    void constructor_whenResultSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, null, resultDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, resultSerializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, resultSerializer, resultDeserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT startOrReply() when first attempt should save operation and return empty optional")
    void startOrReply_whenFirstAttempt_shouldSaveOperationAndReturnEmptyOptional() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.IN_PROCESS);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isEmpty();
        verify(repository, times(1)).saveIfAbsent(mappedOperation);
        verify(repository, never()).update(any(), any());
        verifyNoInteractions(eventListener, conflictHandler, fingerprintManager, resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when concurrent conflict should notify listener and delegate to conflict handler")
    void startOrReply_whenConcurrentConflict_shouldNotifyListenerAndDelegateToConflictHandler() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String response = "response";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.hasConflict()).thenReturn(true);
        when(savedOperation.getState()).thenReturn(OperationState.CONFLICT);
        when(conflictHandler.handle(metadata, String.class)).thenReturn(Optional.of(response));

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(response);
        verify(eventListener, times(1)).onConflict();
        verify(conflictHandler, times(1)).handle(metadata, String.class);
        verify(repository, never()).update(any(), any());
        verifyNoInteractions(fingerprintManager, resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when operation is expired should update operation and return empty optional")
    void startOrReply_whenOperationIsExpired_shouldUpdateOperationAndReturnEmptyOptional() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Operation updatedOperation = mock(Operation.class);
        Instant now = Instant.now();

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.isExpired(now)).thenReturn(true);
        when(repository.update(mappedOperation, OperationState.PROCESSED)).thenReturn(updatedOperation);
        when(updatedOperation.getState()).thenReturn(OperationState.IN_PROCESS);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isEmpty();
        verify(repository, times(1)).update(mappedOperation, OperationState.PROCESSED);
        verifyNoInteractions(eventListener, conflictHandler, fingerprintManager, resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is PROCESSED and fingerprint enabled and fingerprints match should return deserialized result")
    void startOrReply_whenStateIsProcessedAndFingerprintEnabledAndFingerprintsMatch_shouldReturnDeserializedResult() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String existingFingerprint = "existing-fp";
        String incomingFingerprint = "incoming-fp";
        String serialized = "serialized";
        String deserialized = "deserialized";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.PROCESSED);
        when(metadata.useFingerprint()).thenReturn(true);
        when(savedOperation.getFingerprint()).thenReturn(existingFingerprint);
        when(metadata.getFingerprint()).thenReturn(incomingFingerprint);
        when(fingerprintManager.compareWith(existingFingerprint, incomingFingerprint, null)).thenReturn(true);
        when(savedOperation.getResult()).thenReturn(serialized);
        when(resultDeserializer.deserialize(serialized, String.class)).thenReturn(deserialized);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(deserialized);
        verify(fingerprintManager, times(1)).compareWith(existingFingerprint, incomingFingerprint, null);
        verify(fingerprintManager, never()).handleMismatch(any(), any());
        verifyNoInteractions(eventListener, conflictHandler);
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is PROCESSED and fingerprint enabled and fingerprints mismatch should notify listener and return empty")
    void startOrReply_whenStateIsProcessedAndFingerprintEnabledAndFingerprintsMismatch_shouldNotifyListenerAndReturnEmpty() {
        // given
        UUID key = UUID.randomUUID();
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String existingFingerprint = "existing-fp";
        String incomingFingerprint = "incoming-fp";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.PROCESSED);
        when(metadata.useFingerprint()).thenReturn(true);
        when(savedOperation.getFingerprint()).thenReturn(existingFingerprint);
        when(metadata.getFingerprint()).thenReturn(incomingFingerprint);
        when(savedOperation.getIdempotencyKey()).thenReturn(key);
        when(fingerprintManager.compareWith(existingFingerprint, incomingFingerprint, null)).thenReturn(false);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isEmpty();
        verify(eventListener, times(1)).onFingerprintMismatch();
        verify(fingerprintManager, times(1)).handleMismatch(any(DefaultFingerprintMismatchContext.class), isNull());
        verifyNoInteractions(conflictHandler, resultDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is PROCESSED and fingerprint disabled should return deserialized result")
    void startOrReply_whenStateIsProcessedAndFingerprintDisabled_shouldReturnDeserializedResult() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String serialized = "serialized";
        String deserialized = "deserialized";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.PROCESSED);
        when(metadata.useFingerprint()).thenReturn(false);
        when(savedOperation.getResult()).thenReturn(serialized);
        when(resultDeserializer.deserialize(serialized, String.class)).thenReturn(deserialized);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(deserialized);
        verifyNoInteractions(eventListener, conflictHandler, fingerprintManager);
    }

    @Test
    @DisplayName("UT complete() should serialize result update state and return response")
    void complete_shouldSerializeResultUpdateStateAndReturnResponse() {
        // given
        UUID key = UUID.randomUUID();
        OperationMetadata metadata = mock(OperationMetadata.class);
        String response = "response";
        String serialized = "serialized";

        when(metadata.getIdempotencyKey()).thenReturn(key);
        when(resultSerializer.serialize(response)).thenReturn(serialized);

        // when
        String result = tested.complete(metadata, response);

        // then
        assertThat(result).isEqualTo(response);
        verify(resultSerializer, times(1)).serialize(response);
        verify(repository, times(1)).saveResultAndUpdateState(serialized, OperationState.PROCESSED, key, OperationState.IN_PROCESS);
        verifyNoMoreInteractions(resultSerializer, repository);
    }
}
