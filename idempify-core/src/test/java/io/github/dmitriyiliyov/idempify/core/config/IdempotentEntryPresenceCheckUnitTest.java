package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyCoreAutoConfiguration.IdempotentEntryPresenceCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentEntryPresenceCheckUnitTest {

    @Test
    @DisplayName("UT constructor when entries is null should throw NullPointerException")
    void constructor_whenEntriesIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new IdempotentEntryPresenceCheck(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("entries cannot be null");
    }

    @Test
    @DisplayName("UT constructor when no entry is registered should name both ways out and refuse")
    void constructor_whenNoEntryIsRegistered_shouldNameBothWaysOutAndRefuse() {
        // when / then
        assertThatThrownBy(() -> new IdempotentEntryPresenceCheck(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IdempotentEntry is registered")
                .hasMessageContaining("idempify-aop")
                .hasMessageContaining("idempify.enabled=false");
    }

    @Test
    @DisplayName("UT constructor when an entry is registered should accept it")
    void constructor_whenEntryIsRegistered_shouldAcceptIt() {
        // when / then
        assertThatCode(() -> new IdempotentEntryPresenceCheck(List.of(new TestIdempotentEntry())))
                .doesNotThrowAnyException();
    }

    private static final class TestIdempotentEntry implements IdempotentEntry {

        @Override
        public boolean intercepts(Object bean, Method method) {
            return true;
        }
    }
}
