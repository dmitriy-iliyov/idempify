package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictConfigUnitTest {

    @Test
    @DisplayName("UT reject() should select REJECT without a handler of its own")
    void reject_shouldSelectRejectWithoutHandlerOfItsOwn() {
        // when
        ConflictConfig result = ConflictConfig.reject();

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(result.getHandler()).isNull();
        assertThat(result.getHandlerConfig()).isSameAs(ConflictHandlerConfig.NOOP);
    }

    @Test
    @DisplayName("UT wait() should select WAIT and keep the backoff it was given")
    void wait_shouldSelectWaitAndKeepBackoffItWasGiven() {
        // given
        WaitConflictHandlerConfig handlerConfig = WaitConflictHandlerConfig.defaults();

        // when
        ConflictConfig result = ConflictConfig.wait(handlerConfig);

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(result.getHandlerConfig()).isSameAs(handlerConfig);
    }

    @Test
    @DisplayName("UT wait() when the backoff is null should throw NullPointerException")
    void wait_whenBackoffIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.wait(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlerConfig cannot be null");
    }

    @Test
    @DisplayName("UT custom() should keep the handler it was given without naming a strategy")
    void custom_shouldKeepHandlerItWasGivenWithoutNamingStrategy() {
        // given
        ConflictHandler handler = new TestConflictHandler();

        // when
        ConflictConfig result = ConflictConfig.custom(handler);

        // then
        assertThat(result.getStrategy()).isNull();
        assertThat(result.getHandler()).isSameAs(handler);
    }

    @Test
    @DisplayName("UT custom() when the handler is null should throw NullPointerException")
    void custom_whenHandlerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.custom(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler cannot be null");
    }

    @Test
    @DisplayName("UT strategy() when the strategy is null should throw NullPointerException")
    void strategy_whenStrategyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.builder().strategy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("strategy cannot be null");
    }

    @Test
    @DisplayName("UT build() when the strategy is WAIT with a backoff of another kind should throw IllegalArgumentException")
    void build_whenStrategyIsWaitWithBackoffOfAnotherKind_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> ConflictConfig.builder()
                .strategy(ConflictHandleStrategy.WAIT)
                .handlerConfig(new ConflictHandlerConfig() {})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a WaitConflictHandlerConfig");
    }

    @Test
    @DisplayName("UT merge() when the target is disabled should handle no conflict at all")
    void merge_whenTargetIsDisabled_shouldHandleNoConflictAtAll() {
        // when
        ConflictConfig result = ConflictConfig.merge(ConflictConfig.reject(), ConflictConfig.disabled());

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getStrategy()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the reference handles no conflict should let the target switch handling on")
    void merge_whenReferenceHandlesNoConflict_shouldLetTargetSwitchHandlingOn() {
        // when
        ConflictConfig result = ConflictConfig.merge(ConflictConfig.disabled(), ConflictConfig.reject());

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
    }

    @Test
    @DisplayName("UT merge() when the target hands over a handler should keep nothing but that handler")
    void merge_whenTargetHandsOverHandler_shouldKeepNothingButThatHandler() {
        // given
        ConflictHandler handler = new TestConflictHandler();

        // when
        ConflictConfig result = ConflictConfig.merge(
                ConflictConfig.wait(WaitConflictHandlerConfig.defaults()), ConflictConfig.custom(handler));

        // then
        assertThat(result.getStrategy()).isNull();
        assertThat(result.getHandler()).isSameAs(handler);
        assertThat(result.getHandlerConfig()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the target names a strategy should drop the handler the reference supplied")
    void merge_whenTargetNamesStrategy_shouldDropHandlerReferenceSupplied() {
        // given
        ConflictConfig reference = ConflictConfig.custom(new TestConflictHandler());

        // when
        ConflictConfig result = ConflictConfig.merge(reference, ConflictConfig.reject());

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(result.getHandler()).isNull();
    }

    @Test
    @DisplayName("UT merge() when both sides wait should layer the backoff of the target over the reference")
    void merge_whenBothSidesWait_shouldLayerBackoffOfTargetOverReference() {
        // given
        ConflictConfig reference = ConflictConfig.wait(WaitConflictHandlerConfig.builder()
                .delay(10)
                .maxAttempts(2)
                .build());
        ConflictConfig target = ConflictConfig.wait(WaitConflictHandlerConfig.builder()
                .maxAttempts(7)
                .build());

        // when
        ConflictConfig result = ConflictConfig.merge(reference, target);

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        WaitConflictHandlerConfig backoff = (WaitConflictHandlerConfig) result.getHandlerConfig();
        assertThat(backoff.getMaxAttempts()).isEqualTo(7);
        assertThat(backoff.getDelay()).isEqualTo(10L);
    }

    @Test
    @DisplayName("UT merge() when the target waits with a backoff of its own should switch away from rejecting")
    void merge_whenTargetWaitsWithBackoffOfItsOwn_shouldSwitchAwayFromRejecting() {
        // given
        WaitConflictHandlerConfig backoff = WaitConflictHandlerConfig.builder().maxAttempts(7).build();

        // when
        ConflictConfig result = ConflictConfig.merge(ConflictConfig.reject(), ConflictConfig.wait(backoff));

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(result.getHandlerConfig()).isSameAs(backoff);
    }

    @Test
    @DisplayName("UT merge() when the target rejects should drop the backoff the reference waited with")
    void merge_whenTargetRejects_shouldDropBackoffReferenceWaitedWith() {
        // given
        ConflictConfig reference = ConflictConfig.wait(WaitConflictHandlerConfig.defaults());

        // when
        ConflictConfig result = ConflictConfig.merge(reference, ConflictConfig.reject());

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(result.getHandlerConfig()).isNotInstanceOf(WaitConflictHandlerConfig.class);
    }

    @Test
    @DisplayName("UT merge() when the target repeats the reference should keep it untouched")
    void merge_whenTargetRepeatsReference_shouldKeepItUntouched() {
        // given
        ConflictConfig reference = ConflictConfig.wait(WaitConflictHandlerConfig.defaults());

        // when
        ConflictConfig result = ConflictConfig.merge(reference, ConflictConfig.wait(WaitConflictHandlerConfig.defaults()));

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.merge(null, ConflictConfig.reject()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.merge(ConflictConfig.reject(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT equals() when the strategy and the backoff match should be equal and share the hash code")
    void equals_whenStrategyAndBackoffMatch_shouldBeEqualAndShareHashCode() {
        // given
        ConflictConfig one = ConflictConfig.wait(WaitConflictHandlerConfig.builder().maxAttempts(7).build());
        ConflictConfig other = ConflictConfig.wait(WaitConflictHandlerConfig.builder().maxAttempts(7).build());

        // when / then
        assertThat(one).isEqualTo(other).isNotSameAs(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when only the backoff differs should not be equal")
    void equals_whenOnlyBackoffDiffers_shouldNotBeEqual() {
        // given
        ConflictConfig one = ConflictConfig.wait(WaitConflictHandlerConfig.builder().maxAttempts(7).build());
        ConflictConfig other = ConflictConfig.wait(WaitConflictHandlerConfig.defaults());

        // when / then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT toString() should name the strategy it carries")
    void toString_shouldNameStrategyItCarries() {
        assertThat(ConflictConfig.reject().toString()).contains("strategy=REJECT");
    }

    private static final class TestConflictHandler implements ConflictHandler {

        @Override
        public Object handle(ConflictContext context) {
            return null;
        }

        @Override
        public boolean requiresTransaction() {
            return false;
        }
    }

    @Test
    @DisplayName("UT defaults() should reject duplicates, the answer that needs no store and no waiting")
    void defaults_shouldRejectDuplicates() {
        // when
        ConflictConfig result = ConflictConfig.defaults();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(result.getHandler()).isNull();
    }

    @Test
    @DisplayName("UT validate() when a non-waiting strategy carries waiting settings should refuse the pair")
    void validate_whenNonWaitingStrategyCarriesWaitingSettings_shouldRefusePair() {
        // given
        ConflictConfig config = ConflictConfig.builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.REJECT)
                .handlerConfig(WaitConflictHandlerConfig.defaults())
                .build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WAIT");
    }

    @Test
    @DisplayName("UT validate() when the waiting strategy carries no backoff should say so itself")
    void validate_whenWaitingStrategyCarriesNoBackoff_shouldSaySoItself() {
        // given
        ConflictConfig config = ConflictConfig.builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.WAIT)
                .build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConflictHandlerConfig must be specified when ConflictHandleStrategy is WAIT");
    }

    @Test
    @DisplayName("UT validate() when conflict handling is off should not judge settings nobody will read")
    void validate_whenConflictHandlingIsOff_shouldNotJudgeSettingsNobodyWillRead() {
        ConflictConfig.disabled().validate();
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(ConflictConfig.reject()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(ConflictConfig.reject()).isNotEqualTo("REJECT");
    }

    @Test
    @DisplayName("UT equals() when the strategies differ should tell the configs apart")
    void equals_whenStrategiesDiffer_shouldTellConfigsApart() {
        assertThat(ConflictConfig.reject()).isNotEqualTo(ConflictConfig.wait(WaitConflictHandlerConfig.defaults()));
    }


    @Test
    @DisplayName("UT merge() when the target waits without naming a backoff should fall back to the default one")
    void merge_whenTargetWaitsWithoutNamingBackoff_shouldFallBackToDefaultOne() {
        // given
        ConflictConfig target = ConflictConfig.builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.WAIT)
                .build();

        // when
        ConflictConfig result = ConflictConfig.merge(ConflictConfig.reject(), target);

        // then
        assertThat(result.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(result.getHandlerConfig()).isInstanceOf(WaitConflictHandlerConfig.class);
    }

    @Test
    @DisplayName("UT equals() when the handlers differ should tell the configs apart")
    void equals_whenHandlersDiffer_shouldTellConfigsApart() {
        assertThat(ConflictConfig.custom(new TestConflictHandler()))
                .isNotEqualTo(ConflictConfig.custom(new TestConflictHandler()));
    }

    @Test
    @DisplayName("UT build() when nothing was told should decide nothing so the layer below keeps the answer")
    void build_whenNothingWasTold_shouldDecideNothingSoLayerBelowKeepsAnswer() {
        // when
        ConflictConfig result = ConflictConfig.builder().build();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getStrategy()).isNull();
        assertThat(result.getHandler()).isNull();
        assertThat(result.getHandlerConfig()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        ConflictConfig reference = ConflictConfig.wait(WaitConflictHandlerConfig.defaults());

        // when / then
        assertThat(ConflictConfig.merge(reference, ConflictConfig.builder().build())).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT enabled() when the flag is null should throw NullPointerException")
    void enabled_whenFlagIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ConflictConfig.builder().enabled(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("enabled cannot be null");
    }

    @Test
    @DisplayName("UT disabled() should drop the strategy and the handler a config could carry")
    void disabled_shouldDropStrategyAndHandlerConfigCouldCarry() {
        // when
        ConflictConfig result = ConflictConfig.builder(ConflictConfig.wait(WaitConflictHandlerConfig.defaults()))
                .enabled(false)
                .build();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getStrategy()).isNull();
        assertThat(result.getHandlerConfig()).isNull();
        assertThat(result.getHandler()).isNull();
    }

    @Test
    @DisplayName("UT getDefaultHandlerConfig() should answer for every strategy the library ships a handler for")
    void getDefaultHandlerConfig_shouldAnswerForEveryStrategyLibraryShipsHandlerFor() {
        assertThat(ConflictConfig.getDefaultHandlerConfig(ConflictHandleStrategy.REJECT))
                .isEqualTo(ConflictHandlerConfig.NOOP);
        assertThat(ConflictConfig.getDefaultHandlerConfig(ConflictHandleStrategy.WAIT))
                .isEqualTo(WaitConflictHandlerConfig.defaults());
    }

    @Test
    @DisplayName("UT validate() when a handler of its own carries no strategy should accept it")
    void validate_whenHandlerOfItsOwnCarriesNoStrategy_shouldAcceptIt() {
        ConflictConfig.custom(new TestConflictHandler()).validate();
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        ConflictConfig config = ConflictConfig.wait(WaitConflictHandlerConfig.defaults());

        // when
        ConflictConfig copy = ConflictConfig.builder(config)
                .strategy(ConflictHandleStrategy.REJECT)
                .handlerConfig(ConflictHandlerConfig.NOOP)
                .build();

        // then
        assertThat(copy.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
        assertThat(config.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(config.getHandlerConfig()).isEqualTo(WaitConflictHandlerConfig.defaults());
    }

    @Test
    @DisplayName("UT hashCode() when two configs are equal should agree")
    void hashCode_whenTwoConfigsAreEqual_shouldAgree() {
        assertThat(ConflictConfig.reject()).hasSameHashCodeAs(ConflictConfig.defaults());
    }
}
