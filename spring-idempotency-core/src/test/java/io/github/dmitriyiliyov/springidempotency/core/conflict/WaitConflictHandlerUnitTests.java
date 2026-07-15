package io.github.dmitriyiliyov.springidempotency.core.conflict;

import io.github.dmitriyiliyov.springidempotency.core.Operation;
import io.github.dmitriyiliyov.springidempotency.core.OperationRepository;
import io.github.dmitriyiliyov.springidempotency.core.OperationState;
import io.github.dmitriyiliyov.springidempotency.core.ResponseDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WaitConflictHandlerUnitTests {

    @Mock
    OperationRepository repository;

    @Mock
    ResponseDeserializer responseDeserializer;

    @Mock
    Operation operation;

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(null, responseDeserializer, 3, 10L, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when responseDeserializer is null should throw NullPointerException")
    void constructor_whenResponseDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(repository, null, 3, 10L, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when maxAttempt is null should throw NullPointerException")
    void constructor_whenMaxAttemptIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(repository, responseDeserializer, null, 10L, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("maxAttempt cannot be null");
    }

    @Test
    @DisplayName("UT constructor when delay is null should throw NullPointerException")
    void constructor_whenDelayIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(repository, responseDeserializer, 3, null, 2))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delay cannot be null");
    }

    @Test
    @DisplayName("UT constructor when multiplier is null should throw NullPointerException")
    void constructor_whenMultiplierIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(repository, responseDeserializer, 3, 10L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("multiplier cannot be null");
    }

    @Test
    @DisplayName("UT handle() when operation is not found should return empty optional")
    void handle_whenOperationIsNotFound_shouldReturnEmptyOptional() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 3, 0L, 2);

        when(repository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        // when
        Optional<String> result = tested.handle(idempotencyKey, String.class);

        // then
        assertThat(result).isEmpty();

        verify(repository, times(1)).findByIdempotencyKey(idempotencyKey);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(responseDeserializer);
    }

    @Test
    @DisplayName("UT handle() when operation is processed should deserialize and return response")
    void handle_whenOperationIsProcessed_shouldDeserializeAndReturnResponse() {
        // given
        UUID idempotencyKey = UUID.randomUUID();
        String response = "response";

        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 3, 0L, 2);

        when(repository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(operation));

        when(operation.getState())
                .thenReturn(OperationState.PROCESSED);

        when(operation.getResponse())
                .thenReturn(response);

        when(responseDeserializer.deserialize(response, String.class))
                .thenReturn("result");

        // when
        Optional<String> result = tested.handle(idempotencyKey, String.class);

        // then
        assertThat(result)
                .isPresent()
                .contains("result");

        verify(repository, times(1)).findByIdempotencyKey(idempotencyKey);
        verify(operation, times(1)).getState();
        verify(operation, times(1)).getResponse();
        verify(responseDeserializer, times(1)).deserialize(response, String.class);
        verifyNoMoreInteractions(repository, operation, responseDeserializer);
    }

    @Test
    @DisplayName("UT handle() when operation is not processed should wait and retry")
    void handle_whenOperationIsNotProcessed_shouldRetry() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 2, 0L, 2);

        when(repository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(operation));

        when(operation.getState())
                .thenReturn(OperationState.IN_PROCESS);

        // when / then
        assertThatThrownBy(() -> tested.handle(idempotencyKey, String.class))
                .isInstanceOf(WaitTimeoutException.class)
                .hasMessageContaining("did not complete within the configured timeout");

        verify(repository, times(2)).findByIdempotencyKey(idempotencyKey);
        verify(operation, times(2)).getState();
        verifyNoInteractions(responseDeserializer);
    }

    @Test
    @DisplayName("UT handle() when operation never completes should throw WaitTimeoutException")
    void handle_whenOperationNeverCompletes_shouldThrowWaitTimeoutException() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 1, 0L, 2);

        when(repository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(operation));

        when(operation.getState())
                .thenReturn(OperationState.IN_PROCESS);

        // when / then
        assertThatThrownBy(() -> tested.handle(idempotencyKey, String.class))
                .isInstanceOf(WaitTimeoutException.class);

        verify(repository, times(1)).findByIdempotencyKey(idempotencyKey);
    }

    @Test
    @DisplayName("UT handle() when maxAttempt is zero should throw WaitTimeoutException")
    void handle_whenMaxAttemptIsZero_shouldThrowWaitTimeoutException() {
        // given
        UUID idempotencyKey = UUID.randomUUID();

        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 0, 0L, 2);

        // when / then
        assertThatThrownBy(() -> tested.handle(idempotencyKey, String.class))
                .isInstanceOf(WaitTimeoutException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT getStrategy() should return WAIT")
    void getStrategy_shouldReturnWait() {
        // given
        WaitConflictHandler tested = new WaitConflictHandler(repository, responseDeserializer, 1, 0L, 2);

        // when
        ConflictHandleStrategy result = tested.getStrategy();

        // then
        assertThat(result).isEqualTo(ConflictHandleStrategy.WAIT);
    }
}