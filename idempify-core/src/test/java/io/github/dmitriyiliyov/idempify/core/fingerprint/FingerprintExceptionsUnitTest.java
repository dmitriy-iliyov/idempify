package io.github.dmitriyiliyov.idempify.core.fingerprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("UT EmptyFingerprintException should name the key in its message and hand it back")
    void emptyFingerprintException_shouldNameKeyInItsMessageAndHandItBack() {
        // when
        EmptyFingerprintException tested = new EmptyFingerprintException(KEY);

        // then
        assertThat(tested.getMessage()).contains("idempotencyKey=" + KEY);
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            EmptyRequestBodyException.class,
            EmptyFingerprintException.class,
            FingerprintMismatchException.class
    })
    @DisplayName("UT every exception of the fingerprint check should be a FingerprintException so a processor lets it through as one group")
    void exceptionOfFingerprintCheck_shouldBeFingerprintExceptionSoProcessorLetsItThroughAsOneGroup(Class<?> type) {
        assertThat(FingerprintException.class).isAssignableFrom(type);
    }
}
