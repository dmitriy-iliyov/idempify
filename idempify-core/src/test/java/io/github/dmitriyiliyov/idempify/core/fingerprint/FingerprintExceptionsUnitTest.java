package io.github.dmitriyiliyov.idempify.core.fingerprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintExceptionsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT EmptyRequestBodyException should carry the message it was given")
    void emptyRequestBodyException_shouldCarryMessageItWasGiven() {
        // when
        EmptyRequestBodyException tested = new EmptyRequestBodyException("Body is empty");

        // then
        assertThat(tested.getMessage()).isEqualTo("Body is empty");
    }

    @Test
    @DisplayName("UT InvalidFingerprintException should name the key in its message and hand it back")
    void invalidFingerprintException_shouldNameKeyInItsMessageAndHandItBack() {
        // when
        InvalidFingerprintException tested = new InvalidFingerprintException(KEY);

        // then
        assertThat(tested.getMessage()).contains("idempotencyKey=" + KEY);
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
    }
}
