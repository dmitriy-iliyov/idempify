package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The detail carries no expiry: that lives on the record the response is read from, so there is one place
 * saying until when an answer may be replayed rather than two that can disagree.
 */
class DefaultOperationDetailUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT constructor() when idempotencyKey is null should throw NullPointerException")
    void constructor_whenIdempotencyKeyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationDetail(null, OperationStatus.PROCESSED, true, "result"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("idempotencyKey cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when status is null should throw NullPointerException")
    void constructor_whenStatusIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new DefaultOperationDetail(KEY, null, true, "result"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when result is null should keep it, because null is a legal result")
    void constructor_whenResultIsNull_shouldKeepItBecauseNullIsLegalResult() {
        // when
        DefaultOperationDetail tested = new DefaultOperationDetail(KEY, OperationStatus.PROCESSED, true, null);

        // then
        assertThat(tested.getResult()).isNull();
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT getters() should return what the detail was built from")
    void getters_shouldReturnWhatDetailWasBuiltFrom() {
        // when
        DefaultOperationDetail tested = new DefaultOperationDetail(KEY, OperationStatus.IN_PROCESS, false, "result");

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(tested.replayed()).isFalse();
        assertThat(tested.getResult()).isEqualTo("result");
    }

    @Test
    @DisplayName("UT toString() should name every field")
    void toString_shouldNameEveryField() {
        // when
        String printed = new DefaultOperationDetail(KEY, OperationStatus.PROCESSED, true, "result").toString();

        // then
        assertThat(printed).isEqualTo(
                "DefaultOperationDetail{idempotencyKey=%s, status=PROCESSED, replayed=true, result=result}"
                        .formatted(KEY));
    }
}
