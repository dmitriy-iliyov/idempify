package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class IdempotencyEventListenerUnitTest {

    @Test
    @DisplayName("UT NOOP when every hook is called should answer each without failing")
    void noop_whenEveryHookIsCalled_shouldAnswerEachWithoutFailing() {
        // given
        IdempotencyEventListener tested = IdempotencyEventListener.NOOP;

        // when / then
        assertThatNoException().isThrownBy(() -> {
            tested.onDuplicate();
            tested.onConflict();
            tested.onFingerprintMismatch();
            tested.onException();
            tested.onSuccess();
        });
    }
}
