package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DefaultDelegatingConflictHandlerUnitTest {

    @Test
    @DisplayName("UT constructor when handlers is null should throw NullPointerException")
    void constructor_whenHandlersIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultDelegatingConflictHandler(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlers cannot be null");
    }

    @Test
    @DisplayName("UT constructor when multiple handlers for strategy should throw IllegalStateException")
    void constructor_whenMultipleHandlersForStrategy_shouldThrowIllegalStateException() {
        // given
        List<ConflictHandler> handlers = createValidHandlers();
        ConflictHandleStrategy nonCustomStrategy = getNonCustomStrategy();

        if (nonCustomStrategy != null) {
            ConflictHandler extraHandler = mock(ConflictHandler.class);
            lenient().when(extraHandler.getStrategy()).thenReturn(nonCustomStrategy);
            handlers.add(extraHandler);

            // when / then
            assertThatThrownBy(() -> new DefaultDelegatingConflictHandler(handlers))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Exactly one ConflictHandler must be registered for strategy");
        }
    }

    @Test
    @DisplayName("UT constructor when missing handler for non-custom strategy should throw Exception")
    void constructor_whenMissingHandlerForStrategy_shouldThrowException() {
        // given
        ConflictHandleStrategy nonCustomStrategy = getNonCustomStrategy();
        if (nonCustomStrategy != null) {
            List<ConflictHandler> handlers = createValidHandlers().stream()
                    .filter(h -> h.getStrategy() != nonCustomStrategy)
                    .collect(Collectors.toList());

            // when / then
            assertThatThrownBy(() -> new DefaultDelegatingConflictHandler(handlers))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    @DisplayName("UT handle(OperationMetadata, Class) when strategy is CUSTOM and handler class is null should throw IllegalStateException")
    public void handle_whenStrategyIsCustomAndHandlerClassIsNull_shouldThrowIllegalStateException() {
        // given
        DefaultDelegatingConflictHandler tested = new DefaultDelegatingConflictHandler(createValidHandlers());
        OperationMetadata metadata = mock(OperationMetadata.class);

        when(metadata.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.CUSTOM);
        when(metadata.getConflictHandlerClass()).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> tested.handle(metadata, Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConflictHandler class must be specified when using CUSTOM strategy.");
    }

    @Test
    @DisplayName("UT handle(OperationMetadata, Class) when strategy is CUSTOM and handler not found should throw IllegalStateException")
    public void handle_whenStrategyIsCustomAndHandlerNotFound_shouldThrowIllegalStateException() {
        // given
        DefaultDelegatingConflictHandler tested = new DefaultDelegatingConflictHandler(createValidHandlers());
        OperationMetadata metadata = mock(OperationMetadata.class);

        when(metadata.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.CUSTOM);
        doReturn(DummyCustomHandler.class).when(metadata).getConflictHandlerClass();

        // when / then
        assertThatThrownBy(() -> tested.handle(metadata, Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConflictHandler for CUSTOM strategy not found");
    }

    @Test
    @DisplayName("UT handle(OperationMetadata, Class) when strategy is CUSTOM and handler found should call handle and return result")
    public void handle_whenStrategyIsCustomAndHandlerFound_shouldCallHandleAndReturnResult() {
        // given
        List<ConflictHandler> handlers = createValidHandlers();
        ConflictHandler customHandler = mock(TestCustomHandler.class);
        lenient().when(customHandler.getStrategy()).thenReturn(ConflictHandleStrategy.CUSTOM);

        handlers.add(customHandler);

        DefaultDelegatingConflictHandler tested = new DefaultDelegatingConflictHandler(handlers);
        OperationMetadata metadata = mock(OperationMetadata.class);
        UUID idempotencyKey = UUID.randomUUID();

        when(metadata.getConflictHandleStrategy()).thenReturn(ConflictHandleStrategy.CUSTOM);
        doReturn(customHandler.getClass()).when(metadata).getConflictHandlerClass();
        when(metadata.getIdempotencyKey()).thenReturn(idempotencyKey);

        Object expectedResult = new Object();
        when(customHandler.handle(idempotencyKey, Object.class)).thenReturn(Optional.of(expectedResult));

        // when
        Optional<Object> result = tested.handle(metadata, Object.class);

        // then
        assertThat(result).isPresent().contains(expectedResult);
        verify(customHandler, times(1)).handle(idempotencyKey, Object.class);
    }

    @Test
    @DisplayName("UT handle(OperationMetadata, Class) when strategy is not CUSTOM should call handle on first handler")
    public void handle_whenStrategyIsNotCustom_shouldCallHandleOnFirstHandler() {
        // given
        ConflictHandleStrategy nonCustomStrategy = getNonCustomStrategy();
        if (nonCustomStrategy == null) {
            return;
        }

        List<ConflictHandler> handlers = createValidHandlers();
        ConflictHandler targetHandler = handlers.stream()
                .filter(h -> h.getStrategy() == nonCustomStrategy)
                .findFirst()
                .orElseThrow();

        DefaultDelegatingConflictHandler tested = new DefaultDelegatingConflictHandler(handlers);
        OperationMetadata metadata = mock(OperationMetadata.class);
        UUID idempotencyKey = UUID.randomUUID();

        when(metadata.getConflictHandleStrategy()).thenReturn(nonCustomStrategy);
        when(metadata.getIdempotencyKey()).thenReturn(idempotencyKey);

        Object expectedResult = new Object();
        when(targetHandler.handle(idempotencyKey, Object.class)).thenReturn(Optional.of(expectedResult));

        // when
        Optional<Object> result = tested.handle(metadata, Object.class);

        // then
        assertThat(result)
                .isPresent()
                .contains(expectedResult);
        verify(targetHandler, times(1)).handle(idempotencyKey, Object.class);
    }

    private List<ConflictHandler> createValidHandlers() {
        List<ConflictHandler> validHandlers = new ArrayList<>();
        for (ConflictHandleStrategy strategy : ConflictHandleStrategy.values()) {
            ConflictHandler handler = mock(ConflictHandler.class);
            lenient().when(handler.getStrategy()).thenReturn(strategy);
            validHandlers.add(handler);
        }
        return validHandlers;
    }

    private ConflictHandleStrategy getNonCustomStrategy() {
        return Arrays.stream(ConflictHandleStrategy.values())
                .filter(s -> !ConflictHandleStrategy.CUSTOM.equals(s))
                .findFirst()
                .orElse(null);
    }

    private interface DummyCustomHandler extends ConflictHandler { }

    private abstract class TestCustomHandler implements ConflictHandler { }
}
