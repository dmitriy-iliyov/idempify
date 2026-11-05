package io.github.dmitriyiliyov.idempify.core.conflict;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RejectConflictHandlerUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final RejectConflictHandler tested = new RejectConflictHandler();

    @Test
    @DisplayName("UT handle() should throw OperationInProgressException naming the key")
    void handle_shouldThrowOperationInProgressExceptionNamingKey() {
        assertThatThrownBy(() -> tested.handle(new DefaultConflictContext(KEY, null)))
                .isInstanceOf(OperationInProgressException.class)
                .hasMessageContaining(KEY.toString())
                .hasMessageContaining("already in progress");
    }
}
