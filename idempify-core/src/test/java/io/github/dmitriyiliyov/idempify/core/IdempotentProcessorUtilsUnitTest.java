package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;

class IdempotentProcessorUtilsUnitTest {

    @Test
    @DisplayName("UT wrapThrowable() when given a runtime exception should hand back that very exception")
    void wrapThrowable_whenGivenRuntimeException_shouldHandBackThatVeryException() {
        // given
        IllegalStateException thrown = new IllegalStateException("store down");

        // when
        RuntimeException result = IdempotentProcessorUtils.wrapThrowable(thrown, "ignored");

        // then
        assertThat(result).isSameAs(thrown);
    }

    @Test
    @DisplayName("UT wrapThrowable() when given anything but a runtime exception should wrap it under the given message")
    void wrapThrowable_whenGivenAnythingButRuntimeException_shouldWrapItUnderGivenMessage() {
        // given
        Error thrown = new AssertionError("broken");

        // when
        RuntimeException result = IdempotentProcessorUtils.wrapThrowable(thrown, "Operation could not be claimed");

        // then
        assertThat(result)
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Operation could not be claimed")
                .hasCause(thrown);
    }

    @Test
    @DisplayName("UT transactionStatusCheck() when the transaction is the caller's own should let it through")
    void transactionStatusCheck_whenTransactionIsCallersOwn_shouldLetItThrough() {
        assertThatCode(() -> IdempotentProcessorUtils.transactionStatusCheck(new SimpleTransactionStatus(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT transactionStatusCheck() when joining a transaction already running should refuse it")
    void transactionStatusCheck_whenJoiningTransactionAlreadyRunning_shouldRefuseIt() {
        assertThatThrownBy(() -> IdempotentProcessorUtils.transactionStatusCheck(new SimpleTransactionStatus(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@Idempotent must own its transaction");
    }

    @Test
    @DisplayName("UT class should be a utility holder that cannot be instantiated or extended")
    void class_shouldBeUtilityHolderThatCannotBeInstantiatedOrExtended() throws Exception {
        // given
        Constructor<IdempotentProcessorUtils> constructor = IdempotentProcessorUtils.class.getDeclaredConstructor();

        // when / then
        assertThat(Modifier.isFinal(IdempotentProcessorUtils.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
