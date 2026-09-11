package io.github.dmitriyiliyov.idempify.core.conflict;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RejectConflictHandlerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final RejectConflictHandler tested = new RejectConflictHandler();

    @Test
    @DisplayName("UT handle() should throw IdempotencyConflictException naming the key")
    void handle_shouldThrowIdempotencyConflictExceptionNamingKey() {
        assertThatThrownBy(() -> tested.handle(new DefaultConflictContext(KEY, null)))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining(KEY.toString())
                .hasMessageContaining("already in progress");
    }

    @Test
    @DisplayName("UT requiresTransaction() should return true")
    void requiresTransaction_shouldReturnTrue() {
        assertThat(tested.requiresTransaction()).isTrue();
    }
}
