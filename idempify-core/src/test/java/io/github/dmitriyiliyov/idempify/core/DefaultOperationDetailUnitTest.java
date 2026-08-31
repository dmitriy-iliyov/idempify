package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOperationDetailUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-09T12:05:00Z");

    @Test
    @DisplayName("UT constructor when idempotencyKey is null should throw NullPointerException")
    void constructor_whenIdempotencyKeyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationDetail<>(null, OperationStatus.PROCESSED, true, "result", EXPIRES_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey cannot be null");
    }

    @Test
    @DisplayName("UT constructor when status is null should throw NullPointerException")
    void constructor_whenStatusIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationDetail<>(KEY, null, true, "result", EXPIRES_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status cannot be null");
    }

    @Test
    @DisplayName("UT constructor when expiresAt is null should keep it, because an unfinished operation has none")
    void constructor_whenExpiresAtIsNull_shouldKeepItBecauseUnfinishedOperationHasNone() {
        // when
        DefaultOperationDetail<String> tested =
                new DefaultOperationDetail<>(KEY, OperationStatus.IN_PROCESS, false, null, null);

        // then
        assertThat(tested.getExpiresAt()).isNull();
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("UT constructor when result is null should keep it, because null is a legal result")
    void constructor_whenResultIsNull_shouldKeepItBecauseNullIsLegalResult() {
        // when
        DefaultOperationDetail<String> tested =
                new DefaultOperationDetail<>(KEY, OperationStatus.PROCESSED, true, null, EXPIRES_AT);

        // then
        assertThat(tested.getResult()).isNull();
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("UT getters should return what the detail was built from")
    void getters_shouldReturnWhatDetailWasBuiltFrom() {
        // when
        DefaultOperationDetail<String> tested =
                new DefaultOperationDetail<>(KEY, OperationStatus.IN_PROCESS, false, "result", EXPIRES_AT);

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(tested.replayed()).isFalse();
        assertThat(tested.getResult()).isEqualTo("result");
        assertThat(tested.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("UT toString() should name every field")
    void toString_shouldNameEveryField() {
        // when
        String printed = new DefaultOperationDetail<>(KEY, OperationStatus.PROCESSED, true, "result", EXPIRES_AT)
                .toString();

        // then
        assertThat(printed).isEqualTo(
                "DefaultOperationDetail{idempotencyKey=%s, status=PROCESSED, replayed=true, result=result, expiresAt=%s}"
                        .formatted(KEY, EXPIRES_AT));
    }
}
