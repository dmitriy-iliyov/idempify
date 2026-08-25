package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three sentinels of the raw layer answer the same question - "did this call site decide?" - and all
 * three say "no" the same way: as {@code null} once mapped, which is what every merge treats as "ask the
 * layer below".
 */
class TogglesUnitTest {

    @Test
    @DisplayName("UT Toggle.toBoolean() when the switch was left alone should leave the answer to a broader layer")
    void toggleToBoolean_whenSwitchWasLeftAlone_shouldLeaveAnswerToBroaderLayer() {
        assertThat(Toggle.toBoolean(Toggle.UNSELECTED)).isNull();
    }

    @Test
    @DisplayName("UT Toggle.toBoolean() when the switch was set should answer with it")
    void toggleToBoolean_whenSwitchWasSet_shouldAnswerWithIt() {
        assertThat(Toggle.toBoolean(Toggle.ENABLE)).isTrue();
        assertThat(Toggle.toBoolean(Toggle.DISABLE)).isFalse();
    }

    @Test
    @DisplayName("UT ProcessorTypeToggle.toProcessorType() when the processor was left unselected should leave the answer to a broader layer")
    void processorTypeToggleToProcessorType_whenProcessorWasLeftUnselected_shouldLeaveAnswerToBroaderLayer() {
        assertThat(ProcessorTypeToggle.toProcessorType(ProcessorTypeToggle.UNSELECTED)).isNull();
    }

    @Test
    @DisplayName("UT ProcessorTypeToggle.toProcessorType() should map every named choice onto the resolved type")
    void processorTypeToggleToProcessorType_shouldMapEveryNamedChoiceOntoResolvedType() {
        assertThat(ProcessorTypeToggle.toProcessorType(ProcessorTypeToggle.TRANSACTIONAL))
                .isEqualTo(ProcessorType.TRANSACTIONAL);
        assertThat(ProcessorTypeToggle.toProcessorType(ProcessorTypeToggle.LOCK_BASED))
                .isEqualTo(ProcessorType.LOCK_BASED);
    }

    @Test
    @DisplayName("UT ConflictHandleStrategyToggle.toConflictHandleStrategy() when the strategy was left unselected should leave the answer to a broader layer")
    void conflictToggleToStrategy_whenStrategyWasLeftUnselected_shouldLeaveAnswerToBroaderLayer() {
        assertThat(ConflictHandleStrategyToggle.toConflictHandleStrategy(ConflictHandleStrategyToggle.UNSELECTED))
                .isNull();
    }

    @Test
    @DisplayName("UT ConflictHandleStrategyToggle.toConflictHandleStrategy() should map every named choice onto the resolved strategy")
    void conflictToggleToStrategy_shouldMapEveryNamedChoiceOntoResolvedStrategy() {
        assertThat(ConflictHandleStrategyToggle.toConflictHandleStrategy(ConflictHandleStrategyToggle.WAIT))
                .isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(ConflictHandleStrategyToggle.toConflictHandleStrategy(ConflictHandleStrategyToggle.REJECT))
                .isEqualTo(ConflictHandleStrategy.REJECT);
    }

    @Test
    @DisplayName("UT toggles should each carry exactly one value more than the type they resolve to")
    void toggles_shouldEachCarryExactlyOneValueMoreThanTypeTheyResolveTo() {
        assertThat(ProcessorTypeToggle.values()).hasSize(ProcessorType.values().length + 1);
        assertThat(ConflictHandleStrategyToggle.values()).hasSize(ConflictHandleStrategy.values().length + 1);
    }
}
