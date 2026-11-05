package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SwallowIdempotencyEventListenerDecoratorUnitTest {

    @Mock
    private IdempotencyEventListener delegate;

    @InjectMocks
    private SwallowIdempotencyEventListenerDecorator tested;

    @Test
    @DisplayName("UT constructor() when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new SwallowIdempotencyEventListenerDecorator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT onFailure() should hand the failure type on to the delegate")
    void onFailure_shouldHandFailureTypeOnToDelegate() {
        // when
        tested.onFailure(FailureType.OUTCOME_NOT_RECORDED);

        // then
        verify(delegate).onFailure(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("UT onFailure() when the delegate throws should swallow it")
    void onFailure_whenDelegateThrows_shouldSwallowIt() {
        // given
        doThrow(new IllegalStateException("listener down")).when(delegate).onFailure(FailureType.FAILED_BEFORE_RUN);

        // when / then
        assertThatNoException().isThrownBy(() -> tested.onFailure(FailureType.FAILED_BEFORE_RUN));
    }
}
