package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitConflictHandlerConfigUnitTest {

    @Test
    @DisplayName("UT defaults() should hand back the documented backoff")
    void defaults_shouldHandBackDocumentedBackoff() {
        // when
        WaitConflictHandlerConfig result = WaitConflictHandlerConfig.defaults();

        // then
        assertThat(result.getDelay()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_DELAY_MILLIS);
        assertThat(result.getMultiplier()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MULTIPLIER);
        assertThat(result.getMaxAttempts()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_ATTEMPTS);
        assertThat(result.getMaxDuration()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_DURATION_MILLIS);
    }

    @Test
    @DisplayName("UT build() when every setting is given should carry them all")
    void build_whenEverySettingIsGiven_shouldCarryThemAll() {
        // when
        WaitConflictHandlerConfig result = WaitConflictHandlerConfig.builder()
                .delay(Duration.ofMillis(50))
                .multiplier(2.0)
                .maxAttempts(7)
                .maxDuration(Duration.ofSeconds(3))
                .build();

        // then
        assertThat(result.getDelay()).isEqualTo(50L);
        assertThat(result.getMultiplier()).isEqualTo(2.0);
        assertThat(result.getMaxAttempts()).isEqualTo(7);
        assertThat(result.getMaxDuration()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("UT delay() when the delay is not positive should throw IllegalArgumentException")
    void delay_whenDelayIsNotPositive_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().delay(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay cannot be less than 1");
    }

    @Test
    @DisplayName("UT delay() when the delay is null should throw NullPointerException")
    void delay_whenDelayIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().delay(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delay cannot be null");
    }

    @Test
    @DisplayName("UT multiplier() when the multiplier is zero should throw IllegalArgumentException")
    void multiplier_whenMultiplierIsZero_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().multiplier(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier must be positive");
    }

    @Test
    @DisplayName("UT maxAttempts() when the count is zero should throw IllegalArgumentException")
    void maxAttempts_whenCountIsZero_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().maxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts cannot be less than 1");
    }

    @Test
    @DisplayName("UT maxDuration() when the duration is zero should throw IllegalArgumentException")
    void maxDuration_whenDurationIsZero_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().maxDuration(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDuration cannot be less than 1");
    }

    @Test
    @DisplayName("UT maxDuration() when the duration is null should throw NullPointerException")
    void maxDuration_whenDurationIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().maxDuration(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("maxDuration cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target tunes every setting should take them all")
    void merge_whenTargetTunesEverySetting_shouldTakeThemAll() {
        // given
        WaitConflictHandlerConfig target = WaitConflictHandlerConfig.builder()
                .delay(50)
                .multiplier(2.0)
                .maxAttempts(7)
                .maxDuration(3_000)
                .build();

        // when
        ConflictHandlerConfig result = WaitConflictHandlerConfig.merge(tunedReference(), target);

        // then
        assertThat(result).isEqualTo(target);
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        WaitConflictHandlerConfig reference = tunedReference();

        // when
        ConflictHandlerConfig result =
                WaitConflictHandlerConfig.merge(reference, WaitConflictHandlerConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the target asks for the defaults should override the reference with them")
    void merge_whenTargetAsksForDefaults_shouldOverrideReferenceWithThem() {
        // when
        ConflictHandlerConfig result =
                WaitConflictHandlerConfig.merge(tunedReference(), WaitConflictHandlerConfig.defaults());

        // then
        assertThat(result).isEqualTo(WaitConflictHandlerConfig.defaults());
    }

    @Test
    @DisplayName("UT merge() when the target tunes one setting should keep the rest from the reference")
    void merge_whenTargetTunesOneSetting_shouldKeepRestFromReference() {
        // given
        WaitConflictHandlerConfig target = WaitConflictHandlerConfig.builder().maxAttempts(7).build();

        // when
        WaitConflictHandlerConfig result =
                (WaitConflictHandlerConfig) WaitConflictHandlerConfig.merge(tunedReference(), target);

        // then
        assertThat(result.getMaxAttempts()).isEqualTo(7);
        assertThat(result.getDelay()).isEqualTo(10L);
        assertThat(result.getMultiplier()).isEqualTo(3.0);
        assertThat(result.getMaxDuration()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.merge(null, WaitConflictHandlerConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.merge(WaitConflictHandlerConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the reference is a backoff of another kind should throw IllegalArgumentException")
    void merge_whenReferenceIsBackoffOfAnotherKind_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.merge(
                ConflictHandlerConfig.NOOP, WaitConflictHandlerConfig.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference must be a WaitConflictHandlerConfig");
    }

    @Test
    @DisplayName("UT merge() when the target is a backoff of another kind should throw IllegalArgumentException")
    void merge_whenTargetIsBackoffOfAnotherKind_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.merge(
                WaitConflictHandlerConfig.defaults(), ConflictHandlerConfig.NOOP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target must be a WaitConflictHandlerConfig");
    }

    @Test
    @DisplayName("UT equals() when every setting matches should be equal and share the hash code")
    void equals_whenEverySettingMatches_shouldBeEqualAndShareHashCode() {
        // given
        WaitConflictHandlerConfig one = tunedReference();
        WaitConflictHandlerConfig other = tunedReference();

        // when / then
        assertThat(one).isEqualTo(other).isNotSameAs(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when one setting differs should not be equal")
    void equals_whenOneSettingDiffers_shouldNotBeEqual() {
        // given
        WaitConflictHandlerConfig other = WaitConflictHandlerConfig.builder()
                .delay(10)
                .multiplier(3.0)
                .maxAttempts(2)
                .maxDuration(1_001)
                .build();

        // when / then
        assertThat(tunedReference()).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when compared with a backoff of another kind should not be equal")
    void equals_whenComparedWithBackoffOfAnotherKind_shouldNotBeEqual() {
        assertThat(WaitConflictHandlerConfig.defaults()).isNotEqualTo(ConflictHandlerConfig.NOOP);
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        assertThat(WaitConflictHandlerConfig.defaults().toString())
                .contains("delay=5000", "multiplier=1.5", "maxAttempts=5", "maxDuration=60000");
    }

    /**
     * A backoff whose every setting differs from its {@code DEFAULT_*} constant, so that a merge keeping it
     * cannot be confused with a merge falling back to the defaults.
     */
    private static WaitConflictHandlerConfig tunedReference() {
        return WaitConflictHandlerConfig.builder()
                .delay(10)
                .multiplier(3.0)
                .maxAttempts(2)
                .maxDuration(1_000)
                .build();
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        WaitConflictHandlerConfig tested = WaitConflictHandlerConfig.defaults();
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(WaitConflictHandlerConfig.defaults()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(WaitConflictHandlerConfig.defaults()).isNotEqualTo("not a config");
    }

    @Test
    @DisplayName("UT hashCode() when two are equal should agree")
    void hashCode_whenTwoAreEqual_shouldAgree() {
        assertThat(WaitConflictHandlerConfig.defaults()).hasSameHashCodeAs(WaitConflictHandlerConfig.defaults());
    }

    @Test
    @DisplayName("UT notEmpty() when every setting is given should say so")
    void notEmpty_whenEverySettingIsGiven_shouldSaySo() {
        assertThat(WaitConflictHandlerConfig.defaults().notEmpty()).isTrue();
    }

    @Test
    @DisplayName("UT notEmpty() when a setting is left to the layer below should say the config is empty")
    void notEmpty_whenSettingIsLeftToLayerBelow_shouldSayConfigIsEmpty() {
        // given
        WaitConflictHandlerConfig tested = WaitConflictHandlerConfig.builder()
                .delay(1_000L)
                .multiplier(2.0)
                .maxAttempts(3)
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when nothing is given should say the config is empty")
    void notEmpty_whenNothingIsGiven_shouldSayConfigIsEmpty() {
        assertThat(WaitConflictHandlerConfig.builder().build().notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT delay() when given a Duration should keep it in millis")
    void delay_whenGivenDuration_shouldKeepItInMillis() {
        // when
        WaitConflictHandlerConfig result = WaitConflictHandlerConfig.builder().delay(Duration.ofSeconds(3)).build();

        // then
        assertThat(result.getDelay()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("UT maxDuration() when given a Duration should keep it in millis")
    void maxDuration_whenGivenDuration_shouldKeepItInMillis() {
        // when
        WaitConflictHandlerConfig result = WaitConflictHandlerConfig.builder().maxDuration(Duration.ofMinutes(2)).build();

        // then
        assertThat(result.getMaxDuration()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("UT delay() when a Duration rounds down to nothing should throw IllegalArgumentException")
    void delay_whenDurationRoundsDownToNothing_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().delay(Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay cannot be less than 1");
    }

    @Test
    @DisplayName("UT multiplier() when the multiplier is negative should throw IllegalArgumentException")
    void multiplier_whenMultiplierIsNegative_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().multiplier(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiplier must be positive");
    }

    @Test
    @DisplayName("UT maxAttempts() when the count is negative should throw IllegalArgumentException")
    void maxAttempts_whenCountIsNegative_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> WaitConflictHandlerConfig.builder().maxAttempts(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts cannot be less than 1");
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        WaitConflictHandlerConfig config = WaitConflictHandlerConfig.defaults();

        // when
        WaitConflictHandlerConfig copy = WaitConflictHandlerConfig.builder(config).maxAttempts(9).build();

        // then
        assertThat(copy.getMaxAttempts()).isEqualTo(9);
        assertThat(config.getMaxAttempts()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("UT equals() when only the multiplier differs should not be equal")
    void equals_whenOnlyMultiplierDiffers_shouldNotBeEqual() {
        assertThat(WaitConflictHandlerConfig.defaults())
                .isNotEqualTo(WaitConflictHandlerConfig.builder(WaitConflictHandlerConfig.defaults())
                        .multiplier(2.5)
                        .build());
    }

    @Test
    @DisplayName("UT equals() when only the attempt count differs should not be equal")
    void equals_whenOnlyAttemptCountDiffers_shouldNotBeEqual() {
        assertThat(WaitConflictHandlerConfig.defaults())
                .isNotEqualTo(WaitConflictHandlerConfig.builder(WaitConflictHandlerConfig.defaults())
                        .maxAttempts(9)
                        .build());
    }

    @Test
    @DisplayName("UT notEmpty() when only the delay is left to the layer below should say the config is empty")
    void notEmpty_whenOnlyDelayIsLeftToLayerBelow_shouldSayConfigIsEmpty() {
        // given
        WaitConflictHandlerConfig tested = WaitConflictHandlerConfig.builder()
                .multiplier(2.0)
                .maxAttempts(3)
                .maxDuration(10_000L)
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when only the multiplier is left to the layer below should say the config is empty")
    void notEmpty_whenOnlyMultiplierIsLeftToLayerBelow_shouldSayConfigIsEmpty() {
        // given
        WaitConflictHandlerConfig tested = WaitConflictHandlerConfig.builder()
                .delay(1_000L)
                .maxAttempts(3)
                .maxDuration(10_000L)
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when only the attempt count is left to the layer below should say the config is empty")
    void notEmpty_whenOnlyAttemptCountIsLeftToLayerBelow_shouldSayConfigIsEmpty() {
        // given
        WaitConflictHandlerConfig tested = WaitConflictHandlerConfig.builder()
                .delay(1_000L)
                .multiplier(2.0)
                .maxDuration(10_000L)
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }
}
