package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOperationStateUnitTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    @DisplayName("UT getters should hand back the expiry and whether the operation was replayed")
    void getters_shouldHandBackExpiryAndWhetherOperationWasReplayed() {
        // given
        DefaultOperationState tested = new DefaultOperationState(EXPIRES_AT, true);

        // then
        assertThat(tested.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(tested.replayed()).isTrue();
    }

    @Test
    @DisplayName("UT replayed() when the operation ran for real should report false")
    void replayed_whenOperationRanForReal_shouldReportFalse() {
        assertThat(new DefaultOperationState(EXPIRES_AT, false).replayed()).isFalse();
    }

    @Test
    @DisplayName("UT constructor when expiresAt is null should throw NullPointerException")
    void constructor_whenExpiresAtIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationState(null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt cannot be null");
    }

    @Test
    @DisplayName("UT constructor when replayed is null should throw NullPointerException")
    void constructor_whenReplayedIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationState(EXPIRES_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("replayed cannot be null");
    }
}
