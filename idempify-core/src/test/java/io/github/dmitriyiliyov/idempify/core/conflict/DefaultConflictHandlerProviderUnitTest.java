package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.TestClock;
import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Written from the contract of {@link ConflictHandlerProvider}: it resolves the handler that implements a
 * given strategy, and a hand-written handler is outside that lookup because it arrives as an instance and
 * outranks the strategy. "No conflict handling here" has to be an answer it can give, since a config may
 * legitimately carry no conflict section at all.
 */
class DefaultConflictHandlerProviderUnitTest {

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultConflictHandlerProvider(null, resultDeserializer(), clock()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultConflictHandlerProvider(repository(), null, clock()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultConflictHandlerProvider(repository(), resultDeserializer(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT provide() when the config is null should throw NullPointerException")
    void provide_whenConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> tested().provide(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("UT provide() when conflict handling is disabled should answer that nothing handles conflicts here")
    void provide_whenConflictHandlingIsDisabled_shouldAnswerThatNothingHandlesConflictsHere() {
        // when
        ConflictHandler result = tested().provide(ConflictConfig.disabled());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT provide() when the strategy is REJECT should give the handler that refuses the duplicate")
    void provide_whenStrategyIsReject_shouldGiveHandlerThatRefusesDuplicate() {
        // when
        ConflictHandler result = tested().provide(ConflictConfig.reject());

        // then
        assertThat(result).isInstanceOf(RejectConflictHandler.class);
    }

    @Test
    @DisplayName("UT provide() when the strategy is WAIT should give the handler that polls the store")
    void provide_whenStrategyIsWait_shouldGiveHandlerThatPollsStore() {
        // when
        ConflictHandler result = tested().provide(ConflictConfig.wait(WaitConflictHandlerConfig.defaults()));

        // then
        assertThat(result).isInstanceOf(WaitConflictHandler.class);
    }

    @Test
    @DisplayName("UT provide() when the strategy is WAIT without a backoff should build no handler rather than invent one")
    void provide_whenStrategyIsWaitWithoutBackoff_shouldBuildNoHandlerRatherThanInventOne() {
        // given
        ConflictConfig config = ConflictConfig.builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.WAIT)
                .build();

        // when
        ConflictHandler result = tested().provide(config);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT provide() when the strategy was never decided should build no handler")
    void provide_whenStrategyWasNeverDecided_shouldBuildNoHandler() {
        assertThat(tested().provide(ConflictConfig.builder().build())).isNull();
    }

    @Test
    @DisplayName("UT provide() when the config carries a handler of its own should hand back that very instance")
    void provide_whenConfigCarriesHandlerOfItsOwn_shouldHandBackThatVeryInstance() {
        // given
        ConflictHandler custom = new RejectConflictHandler();

        // when
        ConflictHandler result = tested().provide(ConflictConfig.custom(custom));

        // then
        assertThat(result).isSameAs(custom);
    }

    @Test
    @DisplayName("UT provide() when asked twice for the same strategy should give handlers that behave alike")
    void provide_whenAskedTwiceForSameStrategy_shouldGiveHandlersThatBehaveAlike() {
        // given
        DefaultConflictHandlerProvider tested = tested();

        // when
        ConflictHandler first = tested.provide(ConflictConfig.reject());
        ConflictHandler second = tested.provide(ConflictConfig.reject());

        // then
        assertThat(first).isInstanceOf(RejectConflictHandler.class);
        assertThat(second).isInstanceOf(RejectConflictHandler.class);
        assertThat(first.requiresTransaction()).isEqualTo(second.requiresTransaction());
    }

    private static DefaultConflictHandlerProvider tested() {
        return new DefaultConflictHandlerProvider(repository(), resultDeserializer(), clock());
    }

    private static OperationRepository repository() {
        return idempotencyKey -> Optional.empty();
    }

    private static ResultDeserializer resultDeserializer() {
        return (rawResult, type) -> rawResult;
    }

    private static Clock clock() {
        return TestClock.standingStill();
    }
}
