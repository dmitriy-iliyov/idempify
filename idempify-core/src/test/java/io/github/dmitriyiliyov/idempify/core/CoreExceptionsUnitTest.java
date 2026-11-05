package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
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
    @DisplayName("UT OperationLockLostException should name the key whose claim was lost")
    void operationLockLostException_shouldNameKeyWhoseClaimWasLost() {
        // when
        OperationLockLostException tested = new OperationLockLostException(KEY);

        // then
        assertThat(tested)
                .hasMessage("Operation (idempotencyKey=%s) is no longer held under its lock token".formatted(KEY))
                .hasNoCause();
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

    @Test
    @DisplayName("UT OperationLockFailedException should name the key and keep the failure of the claim as its cause")
    void operationLockFailedException_shouldNameKeyAndKeepFailureOfClaimAsItsCause() {
        // given
        IllegalStateException cause = new IllegalStateException("store down");

        // when
        OperationLockFailedException tested = new OperationLockFailedException(KEY, cause);

        // then
        assertThat(tested)
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Operation (idempotencyKey=%s) could not be claimed".formatted(KEY))
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT EarlyFailureException should name the key and keep the failure as its cause")
    void earlyFailureException_shouldNameKeyAndKeepFailureAsItsCause() {
        // given
        IllegalStateException cause = new IllegalStateException("store down");

        // when
        EarlyFailureException tested = new EarlyFailureException(KEY, cause);

        // then
        assertThat(tested)
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Operation (idempotencyKey=%s) failed before the business method ran".formatted(KEY))
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT OperationLockFailedException should belong to the failures before the operation ran so a caller catches both with one type")
    void operationLockFailedException_shouldBelongToFailuresBeforeRunSoCallerCatchesBothWithOneType() {
        assertThat(EarlyFailureException.class).isAssignableFrom(OperationLockFailedException.class);
    }

    @Test
    @DisplayName("UT ResultNotRecordedException should name the key and keep the failure of the store as its cause")
    void resultNotRecordedException_shouldNameKeyAndKeepFailureOfStoreAsItsCause() {
        // given
        IllegalStateException cause = new IllegalStateException("store down");

        // when
        ResultNotRecordedException tested = new ResultNotRecordedException(KEY, cause);

        // then
        assertThat(tested)
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Operation (idempotencyKey=%s) ran, but its result could not be recorded".formatted(KEY))
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT OutcomeNotRecordedException should be abstract so a caller can only meet it as a result or a failure")
    void outcomeNotRecordedException_shouldBeAbstractSoCallerMeetsItOnlyAsResultOrFailure() {
        assertThat(Modifier.isAbstract(OutcomeNotRecordedException.class.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("UT ResultNotRecordedException should belong to the outcomes not recorded so a caller catches both with one type")
    void resultNotRecordedException_shouldBelongToOutcomesNotRecordedSoCallerCatchesBothWithOneType() {
        assertThat(OutcomeNotRecordedException.class).isAssignableFrom(ResultNotRecordedException.class);
    }

    @Test
    @DisplayName("UT FailureNotRecordedException should belong to the outcomes not recorded so a caller catches both with one type")
    void failureNotRecordedException_shouldBelongToOutcomesNotRecordedSoCallerCatchesBothWithOneType() {
        assertThat(OutcomeNotRecordedException.class).isAssignableFrom(FailureNotRecordedException.class);
    }

    @Test
    @DisplayName("UT FailureNotRecordedException should name the key and keep the failure of the store as its cause")
    void failureNotRecordedException_shouldNameKeyAndKeepFailureOfStoreAsItsCause() {
        // given
        IllegalStateException cause = new IllegalStateException("store down");

        // when
        FailureNotRecordedException tested =
                new FailureNotRecordedException(KEY, cause, new IllegalArgumentException("card declined"));

        // then
        assertThat(tested)
                .isInstanceOf(IdempotentProcessingException.class)
                .hasMessage("Operation (idempotencyKey=%s) could not be released or recorded after it failed".formatted(KEY))
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT FailureNotRecordedException should carry what the business method threw as suppressed")
    void failureNotRecordedException_shouldCarryWhatBusinessMethodThrewAsSuppressed() {
        // given
        IllegalArgumentException businessFailure = new IllegalArgumentException("card declined");

        // when
        FailureNotRecordedException tested =
                new FailureNotRecordedException(KEY, new IllegalStateException("store down"), businessFailure);

        // then
        assertThat(tested.getSuppressed()).containsExactly(businessFailure);
    }

    @Test
    @DisplayName("UT FailureNotRecordedException when the store rethrew the business failure itself should not suppress it twice")
    void failureNotRecordedException_whenStoreRethrewBusinessFailureItself_shouldNotSuppressItTwice() {
        // given
        IllegalStateException businessFailure = new IllegalStateException("card declined");

        // when
        FailureNotRecordedException tested = new FailureNotRecordedException(KEY, businessFailure, businessFailure);

        // then
        assertThat(tested).hasCause(businessFailure);
        assertThat(tested.getSuppressed()).isEmpty();
    }

    @Test
    @DisplayName("UT SerializationException should be constructible from a message alone")
    void serializationException_shouldBeConstructibleFromMessageAlone() {
        // when
        SerializationException tested = new SerializationException("Error when serializing");

        // then
        assertThat(tested.getMessage()).isEqualTo("Error when serializing");
        assertThat(tested.getCause()).isNull();
    }

    @Test
    @DisplayName("UT DeserializationException should be constructible from a message alone")
    void deserializationException_shouldBeConstructibleFromMessageAlone() {
        // when
        DeserializationException tested = new DeserializationException("Error when deserializing");

        // then
        assertThat(tested.getMessage()).isEqualTo("Error when deserializing");
        assertThat(tested.getCause()).isNull();
    }

    @Test
    @DisplayName("UT OperationStatusMismatchException should carry the message it was given")
    void operationStatusMismatchException_shouldCarryMessageItWasGiven() {
        // when
        OperationStatusMismatchException tested = new OperationStatusMismatchException("Row is gone");

        // then
        assertThat(tested.getMessage()).isEqualTo("Row is gone");
    }

    @ParameterizedTest
    @ValueSource(classes = {
            IdempotentProcessingException.class,
            ConflictException.class,
            FingerprintException.class,
            IdempotencyKeyException.class,
            SerializationProcessingException.class,
            OperationStatusMismatchException.class,
            OperationLockLostException.class,
            OperationDisappearedException.class
    })
    @DisplayName("UT every exception group of the library should be an IdempifyException so a caller catches them all with one type")
    void exceptionGroupOfLibrary_shouldBeIdempifyExceptionSoCallerCatchesThemAllWithOneType(Class<?> type) {
        assertThat(IdempifyException.class).isAssignableFrom(type);
    }

    @Test
    @DisplayName("UT IdempifyException should be unchecked like every exception of the library")
    void idempifyException_shouldBeUncheckedLikeEveryExceptionOfLibrary() {
        assertThat(RuntimeException.class).isAssignableFrom(IdempifyException.class);
    }

    @Test
    @DisplayName("UT IdempifyException should carry the message and keep the cause it was given")
    void idempifyException_shouldCarryMessageAndKeepCauseItWasGiven() {
        // given
        IllegalStateException cause = new IllegalStateException("store down");

        // when
        IdempifyException tested = new IdempifyException("failed", cause);

        // then
        assertThat(tested).hasMessage("failed").hasCause(cause);
    }
}
