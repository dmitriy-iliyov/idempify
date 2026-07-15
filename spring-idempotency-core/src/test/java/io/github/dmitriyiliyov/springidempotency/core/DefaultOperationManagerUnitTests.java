package io.github.dmitriyiliyov.springidempotency.core;

import io.github.dmitriyiliyov.springidempotency.core.conflict.CompositeConflictHandler;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.DefaultFingerprintMismatchContext;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintManager;
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
class DefaultOperationManagerUnitTests {

    @Mock
    OperationMapper mapper;

    @Mock
    OperationRepository repository;

    @Mock
    IdempotencyEventListener eventListener;

    @Mock
    CompositeConflictHandler conflictHandler;

    @Mock
    FingerprintManager fingerprintManager;

    @Mock
    ResponseSerializer responseSerializer;

    @Mock
    ResponseDeserializer responseDeserializer;

    @Mock
    Clock clock;

    @InjectMocks
    DefaultOperationManager tested;

    @Test
    @DisplayName("UT constructor when mapper is null should throw NullPointerException")
    void constructor_whenMapperIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(null, repository, eventListener, conflictHandler, fingerprintManager, responseSerializer, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("mapper cannot be null");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, null, eventListener, conflictHandler, fingerprintManager, responseSerializer, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, null, conflictHandler, fingerprintManager, responseSerializer, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT constructor when conflictHandler is null should throw NullPointerException")
    void constructor_whenConflictHandlerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, null, fingerprintManager, responseSerializer, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandlerClass cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintManager is null should throw NullPointerException")
    void constructor_whenFingerprintManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, null, responseSerializer, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintManager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when responseSerializer is null should throw NullPointerException")
    void constructor_whenResponseSerializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, null, responseDeserializer, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseSerializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when responseDeserializer is null should throw NullPointerException")
    void constructor_whenResponseDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, responseSerializer, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationManager(mapper, repository, eventListener, conflictHandler, fingerprintManager, responseSerializer, responseDeserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is CONFLICT should notify listener and delegate to conflict handler")
    void startOrReply_whenStateIsConflict_shouldNotifyListenerAndDelegateToConflictHandler() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String response = "response";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.CONFLICT);
        when(conflictHandler.handle(metadata, String.class)).thenReturn(Optional.of(response));

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(response);
        verify(eventListener, times(1)).onConflict();
        verify(conflictHandler, times(1)).handle(metadata, String.class);
        verifyNoInteractions(fingerprintManager, responseDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is PROCESSED and fingerprint enabled and fingerprints match should return deserialized response")
    void startOrReply_whenStateIsProcessedAndFingerprintEnabledAndFingerprintsMatch_shouldReturnDeserializedResponse() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String existingFingerprint = "existing-fp";
        String incomingFingerprint = "incoming-fp";
        String serializedResponse = "serialized";
        String deserializedResponse = "deserialized";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.PROCESSED);
        when(metadata.useFingerprint()).thenReturn(true);
        when(savedOperation.getFingerprint()).thenReturn(existingFingerprint);
        when(metadata.getFingerprint()).thenReturn(incomingFingerprint);
        when(fingerprintManager.compareWith(existingFingerprint, incomingFingerprint, null)).thenReturn(true);
        when(savedOperation.getResponse()).thenReturn(serializedResponse);
        when(responseDeserializer.deserialize(serializedResponse, String.class)).thenReturn(deserializedResponse);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(deserializedResponse);
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
        verifyNoInteractions(conflictHandler, responseDeserializer);
    }

    @Test
    @DisplayName("UT startOrReply() when operation state is PROCESSED and fingerprint disabled should return deserialized response")
    void startOrReply_whenStateIsProcessedAndFingerprintDisabled_shouldReturnDeserializedResponse() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Operation mappedOperation = mock(Operation.class);
        Operation savedOperation = mock(Operation.class);
        Instant now = Instant.now();
        String serializedResponse = "serialized";
        String deserializedResponse = "deserialized";

        when(clock.instant()).thenReturn(now);
        when(mapper.toOperation(metadata, now)).thenReturn(mappedOperation);
        when(repository.saveIfAbsent(mappedOperation)).thenReturn(savedOperation);
        when(savedOperation.getState()).thenReturn(OperationState.PROCESSED);
        when(metadata.useFingerprint()).thenReturn(false);
        when(savedOperation.getResponse()).thenReturn(serializedResponse);
        when(responseDeserializer.deserialize(serializedResponse, String.class)).thenReturn(deserializedResponse);

        // when
        Optional<String> result = tested.startOrReply(metadata, String.class);

        // then
        assertThat(result).isPresent().contains(deserializedResponse);
        verifyNoInteractions(eventListener, conflictHandler, fingerprintManager);
    }

    @Test
    @DisplayName("UT complete() should serialize response update state and return response")
    void complete_shouldSerializeResponseUpdateStateAndReturnResponse() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        String response = "response";
        String serialized = "serialized";

        when(responseSerializer.serialize(response)).thenReturn(serialized);

        // when
        String result = tested.complete(metadata, response);

        // then
        assertThat(result).isEqualTo(response);
        verify(responseSerializer, times(1)).serialize(response);
        verify(repository, times(1)).saveResponseAndUpdateState(serialized, OperationState.IN_PROCESS, OperationState.PROCESSED);
        verifyNoMoreInteractions(responseSerializer, repository);
    }
}