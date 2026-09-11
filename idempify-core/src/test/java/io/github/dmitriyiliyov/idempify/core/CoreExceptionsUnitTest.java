package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exceptions of the core carry the whole diagnosis themselves: the phrase
 * {@code Operation (idempotencyKey=...)} is shared with the log lines, so one grep finds both the record and
 * the failure it belongs to.
 */
class CoreExceptionsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT OperationStatusMismatchException should name the key and the status that was required")
    void operationStatusMismatchException_shouldNameKeyAndStatusThatWasRequired() {
        // when
        OperationStatusMismatchException tested =
                new OperationStatusMismatchException(KEY, OperationStatus.IN_PROCESS);

        // then
        assertThat(tested.getMessage())
                .contains(KEY.toString())
                .contains(OperationStatus.IN_PROCESS.name());
    }

    @Test
    @DisplayName("UT OperationStatusMismatchException should be unchecked so a conditional write need not declare it")
    void operationStatusMismatchException_shouldBeUncheckedSoConditionalWriteNeedNotDeclareIt() {
        assertThat(RuntimeException.class)
                .isAssignableFrom(OperationStatusMismatchException.class);
    }

    @Test
    @DisplayName("UT SerializationException should carry the failure it wraps so the cause is not lost")
    void resultSerializationException_shouldCarryFailureItWrapsSoCauseIsNotLost() {
        // given
        Exception cause = new IllegalStateException("mapper blew up");

        // when
        SerializationException tested = new SerializationException("Error when serializing", cause);

        // then
        assertThat(tested.getMessage()).isEqualTo("Error when serializing");
        assertThat(tested.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("UT DeserializationException should carry the failure it wraps so the cause is not lost")
    void resultDeserializationException_shouldCarryFailureItWrapsSoCauseIsNotLost() {
        // given
        Exception cause = new IllegalStateException("mapper blew up");

        // when
        DeserializationException tested =
                new DeserializationException("Error when deserializing", cause);

        // then
        assertThat(tested.getMessage()).isEqualTo("Error when deserializing");
        assertThat(tested.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("UT SerializationProcessingException should be constructible from a message alone")
    void resultProcessingException_shouldBeConstructibleFromMessageAlone() {
        // when
        SerializationProcessingException tested = new SerializationProcessingException("Error when processing result");

        // then
        assertThat(tested.getMessage()).isEqualTo("Error when processing result");
        assertThat(tested.getCause()).isNull();
    }

    @Test
    @DisplayName("UT DeserializationException should belong to the group a caller catches")
    void resultDeserializationException_shouldBelongToGroupCallerCatches() {
        // given - the transport catches SerializationProcessingException to answer a problem response; a failure
        // that skips the group reaches the container as a bare error instead
        assertThat(SerializationProcessingException.class)
                .isAssignableFrom(DeserializationException.class);
    }

    @Test
    @DisplayName("UT SerializationException should belong to the group a caller catches")
    void resultSerializationException_shouldBelongToGroupCallerCatches() {
        assertThat(SerializationProcessingException.class)
                .isAssignableFrom(SerializationException.class);
    }

    @Test
    @DisplayName("UT SerializationProcessingException should be unchecked like every other exception of the core")
    void resultProcessingException_shouldBeUncheckedLikeEveryOtherExceptionOfCore() {
        assertThat(RuntimeException.class).isAssignableFrom(SerializationProcessingException.class);
    }

    @Test
    @DisplayName("UT result exceptions should keep the cause they wrap so the real failure stays readable")
    void resultExceptions_shouldKeepCauseTheyWrapSoRealFailureStaysReadable() {
        // given
        Exception cause = new IllegalStateException("no creators");

        // then
        assertThat(new DeserializationException("read failed", cause))
                .hasMessage("read failed")
                .hasCause(cause);
        assertThat(new SerializationException("write failed", cause))
                .hasMessage("write failed")
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT IdempotencyKeyException should carry the message it was given")
    void idempotencyKeyException_shouldCarryMessageItWasGiven() {
        // when
        IdempotencyKeyException tested = new IdempotencyKeyException("Idempotency-Key header is missing");

        // then
        assertThat(tested.getMessage()).isEqualTo("Idempotency-Key header is missing");
        assertThat(tested.getCause()).isNull();
    }

    @Test
    @DisplayName("UT IdempotencyKeyException should keep the cause so a malformed key can be traced to the parse failure")
    void idempotencyKeyException_shouldKeepCauseSoMalformedKeyCanBeTracedToParseFailure() {
        // given
        IllegalArgumentException cause = new IllegalArgumentException("Invalid UUID string");

        // when
        IdempotencyKeyException tested = new IdempotencyKeyException("Idempotency-Key is not a UUID", cause);

        // then
        assertThat(tested.getMessage()).isEqualTo("Idempotency-Key is not a UUID");
        assertThat(tested.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("UT IdempotentProcessingException should carry the message it was given")
    void idempotentProcessingException_shouldCarryMessageItWasGiven() {
        // when
        IdempotentProcessingException tested = new IdempotentProcessingException("Surrounded method throws");

        // then
        assertThat(tested.getMessage()).isEqualTo("Surrounded method throws");
        assertThat(tested.getCause()).isNull();
    }

    @Test
    @DisplayName("UT IdempotentProcessingException should keep the cause so a checked failure is not lost in the wrapping")
    void idempotentProcessingException_shouldKeepCauseSoCheckedFailureIsNotLostInWrapping() {
        // given
        Throwable cause = new Exception("checked");

        // when
        IdempotentProcessingException tested = new IdempotentProcessingException("Surrounded method throws", cause);

        // then
        assertThat(tested.getCause()).isSameAs(cause);
    }
}
