package io.github.dmitriyiliyov.springidempotency.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentProcessorUnitTests {

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
    @DisplayName("UT process() when startOrReply returns present optional should notify duplicate and return existing response without calling supplier")
    void process_whenStartOrReplyReturnsPresent_shouldNotifyDuplicateAndReturnExistingResponse() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Supplier<String> supplier = mock(Supplier.class);
        String existingResponse = "existing-response";

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, Object.class)).thenReturn(Optional.of(existingResponse));

        // when
        String result = tested.process(metadata, supplier);

        // then
        assertThat(result).isEqualTo(existingResponse);
        verify(eventListener, times(1)).onDuplicate();
        verifyNoInteractions(supplier);
        verify(manager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() when startOrReply returns empty optional should call supplier complete and notify success")
    void process_whenStartOrReplyReturnsEmpty_shouldCallSupplierCompleteAndNotifySuccess() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Supplier<String> supplier = mock(Supplier.class);
        String supplierResponse = "supplier-response";
        String completedResponse = "completed-response";

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, Object.class)).thenReturn(Optional.empty());
        when(supplier.get()).thenReturn(supplierResponse);
        when(manager.complete(metadata, supplierResponse)).thenReturn(completedResponse);

        // when
        String result = tested.process(metadata, supplier);

        // then
        assertThat(result).isEqualTo(completedResponse);
        verify(supplier, times(1)).get();
        verify(manager, times(1)).complete(metadata, supplierResponse);
        verify(eventListener, times(1)).onSuccess();
        verify(eventListener, never()).onDuplicate();
        verify(eventListener, never()).onException();
    }

    @Test
    @DisplayName("UT process() when supplier throws exception should notify exception and rethrow")
    void process_whenSupplierThrowsException_shouldNotifyExceptionAndRethrow() {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        Supplier<String> supplier = mock(Supplier.class);
        RuntimeException exception = new RuntimeException("supplier error");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        when(manager.startOrReply(metadata, Object.class)).thenReturn(Optional.empty());
        when(supplier.get()).thenThrow(exception);

        // when / then
        assertThatThrownBy(() -> tested.process(metadata, supplier))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("supplier error");

        verify(eventListener, times(1)).onException();
        verify(manager, never()).complete(any(), any());
        verify(eventListener, never()).onSuccess();
        verify(eventListener, never()).onDuplicate();
    }
}