package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.starter.ConflictProperties.WaitConflictProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitConflictPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when delay is null should throw NullPointerException")
    void constructor_whenDelayIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(null, 1.5, 5, Duration.ofSeconds(60)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delay cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when multiplier is null should throw NullPointerException")
    void constructor_whenMultiplierIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), null, 5, Duration.ofSeconds(60)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("multiplier cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when maxAttempts is null should throw NullPointerException")
    void constructor_whenMaxAttemptsIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), 1.5, null, Duration.ofSeconds(60)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maxAttempts cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when maxDuration is null should throw NullPointerException")
    void constructor_whenMaxDurationIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), 1.5, 5, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("maxDuration cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when delay rounds down to nothing should be refused by the bound that owns it")
    void constructor_whenDelayRoundsDownToNothing_shouldBeRefusedByBoundThatOwnsIt() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ZERO, 1.5, 5, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delay cannot be less than 1");
    }

    @Test
    @DisplayName("UT constructor() when multiplier is not positive should be refused by the bound that owns it")
    void constructor_whenMultiplierIsNotPositive_shouldBeRefusedByBoundThatOwnsIt() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), 0.0, 5, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("multiplier must be positive");
    }

    @Test
    @DisplayName("UT constructor() when maxAttempts leaves no attempt should be refused by the bound that owns it")
    void constructor_whenMaxAttemptsLeavesNoAttempt_shouldBeRefusedByBoundThatOwnsIt() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), 1.5, 0, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts cannot be less than 1");
    }

    @Test
    @DisplayName("UT constructor() when maxDuration rounds down to nothing should be refused by the bound that owns it")
    void constructor_whenMaxDurationRoundsDownToNothing_shouldBeRefusedByBoundThatOwnsIt() {
        // when / then
        assertThatThrownBy(() -> new WaitConflictProperties(Duration.ofSeconds(5), 1.5, 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDuration cannot be less than 1");
    }

    @Test
    @DisplayName("UT toWaitConflictHandlerConfig() should hand the core a backoff in millis with nothing left open")
    void toWaitConflictHandlerConfig_shouldHandCoreBackoffInMillisWithNothingLeftOpen() {
        // given
        WaitConflictProperties tested = new WaitConflictProperties(
                Duration.ofMillis(250), 2.0, 3, Duration.ofMinutes(2)
        );

        // when
        WaitConflictHandlerConfig result = tested.toWaitConflictHandlerConfig();

        // then
        assertThat(result.notEmpty()).isTrue();
        assertThat(result.getDelay()).isEqualTo(250L);
        assertThat(result.getMultiplier()).isEqualTo(2.0);
        assertThat(result.getMaxAttempts()).isEqualTo(3);
        assertThat(result.getMaxDuration()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("UT toWaitConflictHandlerConfig() called twice should describe the same backoff")
    void toWaitConflictHandlerConfig_calledTwice_shouldDescribeSameBackoff() {
        // given
        WaitConflictProperties tested = new WaitConflictProperties(
                Duration.ofSeconds(5), 1.5, 5, Duration.ofSeconds(60)
        );

        // when / then
        assertThat(tested.toWaitConflictHandlerConfig()).isEqualTo(tested.toWaitConflictHandlerConfig());
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // given
        WaitConflictProperties tested = new WaitConflictProperties(
                Duration.ofSeconds(5), 1.5, 5, Duration.ofSeconds(60)
        );

        // when / then
        assertThat(tested.getDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(tested.getMultiplier()).isEqualTo(1.5);
        assertThat(tested.getMaxAttempts()).isEqualTo(5);
        assertThat(tested.getMaxDuration()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("UT toString() should name every property it carries")
    void toString_shouldNameEveryPropertyItCarries() {
        // given
        WaitConflictProperties tested = new WaitConflictProperties(
                Duration.ofSeconds(5), 1.5, 5, Duration.ofSeconds(60)
        );

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains("delay=PT5S", "multiplier=1.5", "maxAttempts=5", "maxDuration=PT1M");
    }
}
