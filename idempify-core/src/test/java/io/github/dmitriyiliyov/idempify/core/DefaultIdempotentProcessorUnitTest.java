package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentProcessorUnitTest {

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    OperationManager manager;

    @Mock
    IdempotencyEventListener eventListener;

    @InjectMocks
    DefaultIdempotentProcessor tested;

    @Test
    @DisplayName("UT constructor when transactionTemplate is null should throw NullPointerException")
    void constructor_whenTransactionTemplateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentProcessor(null, manager, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor when manager is null should throw NullPointerException")
    void constructor_whenManagerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentProcessor(transactionTemplate, null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("manager cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentProcessor(transactionTemplate, manager, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT process() when startOrReply returns present optional should notify duplicate and return existing response without calling operation")
    void process_whenStartOrReplyReturnsPresent_shouldNotifyDuplicateAndReturnExistingResponse() throws Throwable {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        String existingResponse = "existing-response";

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, String.class)).thenReturn(Optional.of(existingResponse));

        // when
        String result = tested.process(metadata, String.class, operation);

        // then
        assertThat(result).isEqualTo(existingResponse);
        verify(eventListener, times(1)).onDuplicate();
        verifyNoInteractions(operation);
        verify(manager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() when startOrReply returns empty optional should call operation complete and notify success")
    void process_whenStartOrReplyReturnsEmpty_shouldCallOperationCompleteAndNotifySuccess() throws Throwable {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        String operationResponse = "operation-response";
        String completedResponse = "completed-response";

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, String.class)).thenReturn(Optional.empty());
        when(operation.call()).thenReturn(operationResponse);
        when(manager.complete(metadata, operationResponse)).thenReturn(completedResponse);

        // when
        String result = tested.process(metadata, String.class, operation);

        // then
        assertThat(result).isEqualTo(completedResponse);
        verify(operation, times(1)).call();
        verify(manager, times(1)).complete(metadata, operationResponse);
        verify(eventListener, times(1)).onSuccess();
        verify(eventListener, never()).onDuplicate();
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() when operation throws checked exception should wrap it in RuntimeException notify exception and rethrow")
    void process_whenOperationThrowsCheckedException_shouldWrapInRuntimeExceptionNotifyExceptionAndRethrow() throws Throwable {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        Exception checkedException = new Exception("checked error");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, String.class)).thenReturn(Optional.empty());
        when(operation.call()).thenThrow(checkedException);

        // when / then
        assertThatThrownBy(() -> tested.process(metadata, String.class, operation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error when processing surrounded method")
                .hasCause(checkedException);

        verify(eventListener, times(1)).onException();
        verify(manager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
        verify(eventListener, never()).onDuplicate();
    }

    @Test
    @DisplayName("UT process() when operation throws RuntimeException should wrap it notify exception and rethrow")
    void process_whenOperationThrowsRuntimeException_shouldWrapNotifyExceptionAndRethrow() throws Throwable {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        IdempotentOperation<String> operation = mock(IdempotentOperation.class);
        RuntimeException operationException = new RuntimeException("operation error");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, String.class)).thenReturn(Optional.empty());
        when(operation.call()).thenThrow(operationException);

        // when / then
        assertThatThrownBy(() -> tested.process(metadata, String.class, operation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error when processing surrounded method")
                .hasCause(operationException);

        verify(eventListener, times(1)).onException();
        verify(manager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
        verify(eventListener, never()).onDuplicate();
    }
}
