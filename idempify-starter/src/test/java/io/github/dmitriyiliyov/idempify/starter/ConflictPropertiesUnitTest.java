package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;
import io.github.dmitriyiliyov.idempify.core.config.ConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.starter.ConflictProperties.WaitConflictProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ConflictProperties(null, ConflictHandleStrategy.REJECT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when strategy is null should throw NullPointerException")
    void constructor_whenStrategyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ConflictProperties(true, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("strategy cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the wait block is given to a strategy that cannot read it should throw IllegalStateException")
    void constructor_whenWaitBlockIsGivenToStrategyThatCannotReadIt_shouldThrowIllegalStateException() {
        // when / then
        assertThatThrownBy(() -> new ConflictProperties(true, ConflictHandleStrategy.REJECT, waitProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("wait should not be specified when strategy is REJECT");
    }

    @Test
    @DisplayName("UT toConflictConfig() when strategy is REJECT should carry the config of a handler with nothing to tune")
    void toConflictConfig_whenStrategyIsReject_shouldCarryConfigOfHandlerWithNothingToTune() {
        // given
        ConflictProperties tested = new ConflictProperties(true, ConflictHandleStrategy.REJECT, null);

        // when
        ConflictConfig result = tested.toConflictConfig();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(result.getHandlerConfig()).isEqualTo(ConflictHandlerConfig.NOOP);
        assertThat(result.getHandler()).isNull();
    }

    @Test
    @DisplayName("UT toConflictConfig() when strategy is WAIT without its block should answer the whole backoff anyway")
    void toConflictConfig_whenStrategyIsWaitWithoutItsBlock_shouldAnswerWholeBackoffAnyway() {
        // given
        ConflictProperties tested = new ConflictProperties(true, ConflictHandleStrategy.WAIT, null);

        // when
        ConflictConfig result = tested.toConflictConfig();

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(result.getHandlerConfig()).isEqualTo(WaitConflictHandlerConfig.defaults());
        assertThat(((WaitConflictHandlerConfig) result.getHandlerConfig()).notEmpty()).isTrue();
    }

    @Test
    @DisplayName("UT toConflictConfig() when the wait block is given should carry its values")
    void toConflictConfig_whenWaitBlockIsGiven_shouldCarryItsValues() {
        // given
        WaitConflictProperties waitProperties = new WaitConflictProperties(
                Duration.ofSeconds(2), 3.0, 7, Duration.ofSeconds(90)
        );
        ConflictProperties tested = new ConflictProperties(true, ConflictHandleStrategy.WAIT, waitProperties);

        // when
        WaitConflictHandlerConfig result = (WaitConflictHandlerConfig) tested.toConflictConfig().getHandlerConfig();

        // then
        assertThat(result.getDelay()).isEqualTo(2_000L);
        assertThat(result.getMultiplier()).isEqualTo(3.0);
        assertThat(result.getMaxAttempts()).isEqualTo(7);
        assertThat(result.getMaxDuration()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("UT toConflictConfig() whatever the strategy should produce a config the core accepts")
    void toConflictConfig_whateverTheStrategy_shouldProduceConfigCoreAccepts() {
        // given / when / then
        for (ConflictHandleStrategy strategy : ConflictHandleStrategy.values()) {
            ConflictConfig result = new ConflictProperties(true, strategy, null).toConflictConfig();
            assertThat(result.getStrategy()).isEqualTo(strategy);
            result.validate();
        }
    }

    @Test
    @DisplayName("UT toConflictConfig() when conflict handling is disabled should leave no strategy behind")
    void toConflictConfig_whenConflictHandlingIsDisabled_shouldLeaveNoStrategyBehind() {
        // given
        ConflictProperties tested = new ConflictProperties(false, ConflictHandleStrategy.REJECT, null);

        // when
        ConflictConfig result = tested.toConflictConfig();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getStrategy()).isNull();
        assertThat(result.getHandlerConfig()).isNull();
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // given
        WaitConflictProperties waitProperties = waitProperties();

        // when
        ConflictProperties tested = new ConflictProperties(true, ConflictHandleStrategy.WAIT, waitProperties);

        // then
        assertThat(tested.isEnabled()).isTrue();
        assertThat(tested.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(tested.getWait()).isSameAs(waitProperties);
    }

    @Test
    @DisplayName("UT toString() should name every property it carries")
    void toString_shouldNameEveryPropertyItCarries() {
        // given
        ConflictProperties tested = new ConflictProperties(true, ConflictHandleStrategy.WAIT, waitProperties());

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains("enabled=true", "strategy=WAIT", "wait=WaitConflictProperties{");
    }

    private static WaitConflictProperties waitProperties() {
        return new WaitConflictProperties(Duration.ofSeconds(5), 1.5, 5, Duration.ofSeconds(60));
    }
}
