package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentProcessorUtilsUnitTest {

    @Test
    @DisplayName("UT getResult() should return what the operation returned")
    void getResult_shouldReturnWhatOperationReturned() {
        assertThat(IdempotentProcessorUtils.getResult(() -> "charged")).isEqualTo("charged");
    }

    @Test
    @DisplayName("UT getResult() when the operation returned null should hand that null back")
    void getResult_whenOperationReturnedNull_shouldHandThatNullBack() {
        assertThat(IdempotentProcessorUtils.<String>getResult(() -> null)).isNull();
    }

    @Test
    @DisplayName("UT getResult() when the operation throws a runtime exception should let it out unchanged")
    void getResult_whenOperationThrowsRuntimeException_shouldLetItOutUnchanged() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> IdempotentProcessorUtils.getResult(() -> {
            throw thrown;
        })).isSameAs(thrown);
    }

    @Test
    @DisplayName("UT getResult() when the operation throws a checked exception should wrap it in IdempotentProcessingException")
    void getResult_whenOperationThrowsCheckedException_shouldWrapItInIdempotentProcessingException() {
        // given
        Exception thrown = new Exception("checked blew up");

        // when / then
        assertThatThrownBy(() -> IdempotentProcessorUtils.getResult(() -> {
            throw thrown;
        }))
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Surrounded method throws")
                .hasCause(thrown);
    }

    @Test
    @DisplayName("UT class should be a utility holder that cannot be instantiated or extended")
    void class_shouldBeUtilityHolderThatCannotBeInstantiatedOrExtended() throws Exception {
        Constructor<IdempotentProcessorUtils> constructor = IdempotentProcessorUtils.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(IdempotentProcessorUtils.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
