package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractIdempotentProcessorDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    IdempotentProcessor delegate;

    @Test
    @DisplayName("UT constructor when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new PassThroughDecorator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delegate cannot be null");
    }

    @Test
    @DisplayName("UT process() should pass the call straight through to the delegate")
    void process_shouldPassCallStraightThroughToDelegate() {
        // given
        PassThroughDecorator tested = new PassThroughDecorator(delegate);
        OperationContext context = new DefaultOperationContext(ResultType.ofClass(String.class), () -> "fresh", KEY, null);
        OperationMetadata metadata = TestOperationMetadata.builder().build();

        when(delegate.process(context, metadata)).thenReturn("from-delegate");

        // when
        Object result = tested.process(context, metadata);

        // then
        assertThat(result).isEqualTo("from-delegate");
        verify(delegate, times(1)).process(context, metadata);
    }

    private static final class PassThroughDecorator extends AbstractIdempotentProcessorDecorator {

        private PassThroughDecorator(IdempotentProcessor delegate) {
            super(delegate);
        }
    }
}
