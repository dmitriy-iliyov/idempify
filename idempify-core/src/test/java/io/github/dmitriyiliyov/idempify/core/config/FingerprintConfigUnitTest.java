package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FingerprintConfigUnitTest {

    @Test
    @DisplayName("UT disabled() should turn fingerprinting off and carry no other setting")
    void disabled_shouldTurnFingerprintingOffAndCarryNoOtherSetting() {
        // when
        FingerprintConfig result = FingerprintConfig.disabled();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getFingerprintPolicy()).isNull();
        assertThat(result.getBodyHandleStrategy()).isNull();
        assertThat(result.getEmptyBodyFallback()).isNull();
        assertThat(result.getBodyCanonicalizer()).isNull();
        assertThat(result.getBodyCanonicalizerConfig()).isNull();
    }

    @Test
    @DisplayName("UT defaults() should fill every setting with the library's own answer")
    void defaults_shouldFillEverySettingWithTheLibrarySOwnAnswer() {
        // when
        FingerprintConfig result = FingerprintConfig.defaults();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.enabledAndEmpty()).isFalse();
        assertThat(result.getBodyHandleStrategy()).isEqualTo(FingerprintConfig.DEFAULT_BODY_HANDLE_STRATEGY);
        assertThat(result.getEmptyBodyFallback()).isSameAs(FingerprintConfig.DEFAULT_EMPTY_BODY_FALLBACK);
        assertThat(result.getBodyCanonicalizerConfig()).isEqualTo(FingerprintConfig.DEFAULT_BODY_CANONICALIZER_CONFIG);
    }

    @Test
    @DisplayName("UT build() when only the policy is set should carry it and pick no strategy of its own")
    void build_whenOnlyPolicyIsSet_shouldCarryItAndPickNoStrategyOfItsOwn() {
        // given
        FingerprintPolicy policy = policy();

        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .fingerprintPolicy(policy)
                .build();

        // then
        assertThat(result.getFingerprintPolicy()).isSameAs(policy);
        assertThat(result.getBodyHandleStrategy()).isNull();
        assertThat(result.enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT build() when the policy comes with an empty-body fallback should throw IllegalStateException")
    void build_whenPolicyComesWithEmptyBodyFallback_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .fingerprintPolicy(policy())
                .emptyBodyFallback(EmptyBodyFallback.NOOP);

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it cannot be combined with any other setting");
    }

    @Test
    @DisplayName("UT build() when a byte-level strategy is set should carry it and leave the canonicalizer unset")
    void build_whenByteLevelStrategyIsSet_shouldCarryItAndLeaveCanonicalizerUnset() {
        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.RAW_BYTES_HASH);
        assertThat(result.getBodyCanonicalizer()).isNull();
        assertThat(result.getBodyCanonicalizerConfig()).isNull();
    }

    @Test
    @DisplayName("UT build() when a byte-level strategy comes with an empty-body fallback should carry both")
    void build_whenByteLevelStrategyComesWithEmptyBodyFallback_shouldCarryBoth() {
        // given
        EmptyBodyFallback fallback = EmptyBodyFallback.NOOP;

        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .emptyBodyFallback(fallback)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.NORMALIZED_BYTES_HASH);
        assertThat(result.getEmptyBodyFallback()).isSameAs(fallback);
    }

    @Test
    @DisplayName("UT build() when only the canonicalized strategy is set should leave the canonicalizer to the defaults")
    void build_whenOnlyCanonicalizedStrategyIsSet_shouldLeaveCanonicalizerToDefaults() {
        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.CANONICALIZED_BODY_HASH)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizer()).isNull();
        assertThat(result.getBodyCanonicalizerConfig()).isNull();
    }

    @Test
    @DisplayName("UT build() when only the canonicalizer is set should select CANONICALIZED_BODY_HASH")
    void build_whenOnlyCanonicalizerIsSet_shouldSelectCanonicalizedBodyHash() {
        // given
        BodyCanonicalizer canonicalizer = canonicalizer();

        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyCanonicalizer(canonicalizer)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizer()).isSameAs(canonicalizer);
    }

    @Test
    @DisplayName("UT build() when the canonicalizer is set alongside its own strategy should carry both")
    void build_whenCanonicalizerIsSetAlongsideItsOwnStrategy_shouldCarryBoth() {
        // given
        BodyCanonicalizer canonicalizer = canonicalizer();

        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.CANONICALIZED_BODY_HASH)
                .bodyCanonicalizer(canonicalizer)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizer()).isSameAs(canonicalizer);
    }

    @Test
    @DisplayName("UT build() when only the canonicalizer config is set should select CANONICALIZED_BODY_HASH")
    void build_whenOnlyCanonicalizerConfigIsSet_shouldSelectCanonicalizedBodyHash() {
        // given
        BodyCanonicalizerConfig canonicalizerConfig = BodyCanonicalizerConfig.defaults();

        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(canonicalizerConfig)
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizerConfig()).isSameAs(canonicalizerConfig);
    }

    @Test
    @DisplayName("UT build() when the canonicalizer config is built from a consumer should carry what the consumer set")
    void build_whenCanonicalizerConfigIsBuiltFromConsumer_shouldCarryWhatConsumerSet() {
        // when
        FingerprintConfig result = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.excludedFields("requestedAt"))
                .build();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizerConfig().getExcludedFields()).containsExactly("requestedAt");
    }

    @Test
    @DisplayName("UT build() when the empty-body fallback is the only setting should throw IllegalStateException")
    void build_whenEmptyBodyFallbackIsOnlySetting_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .emptyBodyFallback(EmptyBodyFallback.NOOP);

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it cannot be the only setting");
    }

    @Test
    @DisplayName("UT build() when disabled carries a policy should throw IllegalStateException")
    void build_whenDisabledCarriesPolicy_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .enabled(false)
                .fingerprintPolicy(policy());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled fingerprinting cannot carry any other setting");
    }

    @Test
    @DisplayName("UT build() when disabled carries an empty-body fallback should throw IllegalStateException")
    void build_whenDisabledCarriesEmptyBodyFallback_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .enabled(false)
                .emptyBodyFallback(EmptyBodyFallback.NOOP);

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled fingerprinting cannot carry any other setting");
    }

    @Test
    @DisplayName("UT build() when the policy comes with a strategy should throw IllegalStateException")
    void build_whenPolicyComesWithStrategy_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .fingerprintPolicy(policy())
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH);

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it cannot be combined with any other setting");
    }

    @Test
    @DisplayName("UT build() when the policy comes with a canonicalizer should throw IllegalStateException")
    void build_whenPolicyComesWithCanonicalizer_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .fingerprintPolicy(policy())
                .bodyCanonicalizer(canonicalizer());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it cannot be combined with any other setting");
    }

    @Test
    @DisplayName("UT build() when the policy comes with a canonicalizer config should throw IllegalStateException")
    void build_whenPolicyComesWithCanonicalizerConfig_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .fingerprintPolicy(policy())
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it cannot be combined with any other setting");
    }

    @Test
    @DisplayName("UT build() when a byte-level strategy comes with a canonicalizer should throw IllegalStateException")
    void build_whenByteLevelStrategyComesWithCanonicalizer_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .bodyCanonicalizer(canonicalizer());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonicalizer settings apply to %s only".formatted(BodyHandleStrategy.CANONICALIZED_BODY_HASH));
    }

    @Test
    @DisplayName("UT build() when a byte-level strategy comes with a canonicalizer config should throw IllegalStateException")
    void build_whenByteLevelStrategyComesWithCanonicalizerConfig_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonicalizer settings apply to %s only".formatted(BodyHandleStrategy.CANONICALIZED_BODY_HASH));
    }

    @Test
    @DisplayName("UT build() when the canonicalizer comes with a canonicalizer config should throw IllegalStateException")
    void build_whenCanonicalizerComesWithCanonicalizerConfig_shouldThrowIllegalStateException() {
        // given
        FingerprintConfig.Builder tested = FingerprintConfig.builder()
                .bodyCanonicalizer(canonicalizer())
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults());

        // when / then
        assertThatThrownBy(tested::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot take a bodyCanonicalizerConfig");
    }

    @Test
    @DisplayName("UT bodyCanonicalizerConfig() when the consumer is null should throw NullPointerException")
    void bodyCanonicalizerConfig_whenConsumerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().bodyCanonicalizerConfig((Consumer<BodyCanonicalizerConfig.Builder>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configBuilder cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is disabled should turn fingerprinting off")
    void merge_whenTargetIsDisabled_shouldTurnFingerprintingOff() {
        // when
        FingerprintConfig result = FingerprintConfig.merge(FingerprintConfig.defaults(), FingerprintConfig.disabled());

        // then
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the target carries a policy should drop everything the reference tuned")
    void merge_whenTargetCarriesPolicy_shouldDropEverythingReferenceTuned() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.excludedFields("requestedAt"))
                .build();
        FingerprintPolicy policy = policy();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, FingerprintConfig.builder()
                .fingerprintPolicy(policy)
                .build());

        // then
        assertThat(result.getFingerprintPolicy()).isSameAs(policy);
        assertThat(result.getBodyHandleStrategy()).isNull();
        assertThat(result.getBodyCanonicalizerConfig()).isNull();
        assertThat(result.getEmptyBodyFallback()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the target takes the defaults should override the reference with them")
    void merge_whenTargetTakesDefaults_shouldOverrideReferenceWithThem() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .emptyBodyFallback(EmptyBodyFallback.NOOP)
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, FingerprintConfig.defaults());

        // then
        assertThat(result).isEqualTo(FingerprintConfig.defaults());
    }

    @Test
    @DisplayName("UT merge() when the target tunes nothing should keep the reference untouched")
    void merge_whenTargetTunesNothing_shouldKeepReferenceUntouched() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .emptyBodyFallback(EmptyBodyFallback.NOOP)
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, FingerprintConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the target tunes the canonicalizer over a reference that tuned none should start from the default one")
    void merge_whenTargetTunesCanonicalizerOverReferenceThatTunedNone_shouldStartFromDefaultOne() {
        // given
        FingerprintConfig target = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.excludedFields("requestedAt"))
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(FingerprintConfig.defaults(), target);

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizerConfig().getExcludedFields()).containsExactly("requestedAt");
        assertThat(result.getBodyCanonicalizerConfig().getFormat())
                .isEqualTo(FingerprintConfig.DEFAULT_BODY_CANONICALIZER_CONFIG.getFormat());
    }

    @Test
    @DisplayName("UT merge() when both sides tune the canonicalizer should layer the target over the reference")
    void merge_whenBothSidesTuneCanonicalizer_shouldLayerTargetOverReference() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.format(BodyFormat.XML).excludedFields("requestedAt"))
                .build();
        FingerprintConfig target = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.includedFields("amount"))
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, target);

        // then
        assertThat(result.getBodyCanonicalizerConfig().getFormat()).isEqualTo(BodyFormat.XML);
        assertThat(result.getBodyCanonicalizerConfig().getIncludedFields()).containsExactly("amount");
        assertThat(result.getBodyCanonicalizerConfig().getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT merge() when the target hands over a canonicalizer of its own should take it")
    void merge_whenTargetHandsOverCanonicalizerOfItsOwn_shouldTakeIt() {
        // given
        BodyCanonicalizer canonicalizer = canonicalizer();
        FingerprintConfig target = FingerprintConfig.builder().bodyCanonicalizer(canonicalizer).build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(FingerprintConfig.defaults(), target);

        // then
        assertThat(result.getBodyCanonicalizer()).isSameAs(canonicalizer);
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
    }

    @Test
    @DisplayName("UT merge() when the target only replaces the empty-body fallback should keep the reference strategy")
    void merge_whenTargetOnlyReplacesEmptyBodyFallback_shouldKeepReferenceStrategy() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .build();
        FingerprintConfig target = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .emptyBodyFallback(EmptyBodyFallback.NOOP)
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, target);

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.NORMALIZED_BYTES_HASH);
        assertThat(result.getEmptyBodyFallback()).isSameAs(EmptyBodyFallback.NOOP);
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.merge(null, FingerprintConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.merge(FingerprintConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT equals() when both sides tune nothing should be equal and share the hash code")
    void equals_whenBothSidesTuneNothing_shouldBeEqualAndShareHashCode() {
        // given
        FingerprintConfig one = FingerprintConfig.builder().build();
        FingerprintConfig other = FingerprintConfig.builder().build();

        // when / then
        assertThat(one).isEqualTo(other).isNotSameAs(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when one side took the defaults and the other tuned nothing should tell them apart")
    void equals_whenOneSideTookDefaultsAndOtherTunedNothing_shouldTellThemApart() {
        // given
        FingerprintConfig filled = FingerprintConfig.defaults();
        FingerprintConfig empty = FingerprintConfig.builder().build();

        // when / then
        assertThat(filled).isNotEqualTo(empty);
    }

    @Test
    @DisplayName("UT equals() when the canonicalizer settings match should be equal and share the hash code")
    void equals_whenCanonicalizerSettingsMatch_shouldBeEqualAndShareHashCode() {
        // given
        FingerprintConfig one = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.excludedFields("requestedAt"))
                .build();
        FingerprintConfig other = FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder.excludedFields("requestedAt"))
                .build();

        // when / then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the strategy differs should not be equal")
    void equals_whenStrategyDiffers_shouldNotBeEqual() {
        // given
        FingerprintConfig one = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build();
        FingerprintConfig other = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .build();

        // when / then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when the policies are equivalent but built apart should not be equal")
    void equals_whenPoliciesAreEquivalentButBuiltApart_shouldNotBeEqual() {
        // given
        FingerprintConfig one = FingerprintConfig.builder().fingerprintPolicy(policy()).build();
        FingerprintConfig other = FingerprintConfig.builder().fingerprintPolicy(policy()).build();

        // when / then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when a disabled config is compared with the defaults should not be equal")
    void equals_whenDisabledConfigIsComparedWithDefaults_shouldNotBeEqual() {
        assertThat(FingerprintConfig.disabled()).isNotEqualTo(FingerprintConfig.defaults());
    }

    @Test
    @DisplayName("UT toString() should name the strategy it carries")
    void toString_shouldNameStrategyItCarries() {
        // given
        FingerprintConfig tested = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build();

        // when / then
        assertThat(tested.toString()).contains("bodyHandleStrategy=RAW_BYTES_HASH");
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(FingerprintConfig.defaults()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(FingerprintConfig.defaults()).isNotEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when fingerprinting is off should not report an empty enabled config")
    void enabledAndEmpty_whenFingerprintingIsOff_shouldNotReportEmptyEnabledConfig() {
        assertThat(FingerprintConfig.disabled().enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT equals() when the strategies differ should tell the configs apart")
    void equals_whenStrategiesDiffer_shouldTellConfigsApart() {
        assertThat(FingerprintConfig.builder().bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH).build())
                .isNotEqualTo(FingerprintConfig.builder().bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH).build());
    }

    @Test
    @DisplayName("UT equals() when one side is switched off should tell the configs apart")
    void equals_whenOneSideIsSwitchedOff_shouldTellConfigsApart() {
        assertThat(FingerprintConfig.disabled()).isNotEqualTo(FingerprintConfig.builder().build());
    }

    @Test
    @DisplayName("UT build() when nothing was told should decide nothing so the layer below keeps the answer")
    void build_whenNothingWasTold_shouldDecideNothingSoLayerBelowKeepsAnswer() {
        // when
        FingerprintConfig result = FingerprintConfig.builder().build();

        // then
        assertThat(result.enabledAndEmpty()).isTrue();
        assertThat(result).isNotEqualTo(FingerprintConfig.defaults());
        assertThat(result).isNotEqualTo(FingerprintConfig.disabled());
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build();

        // when / then
        assertThat(FingerprintConfig.merge(reference, FingerprintConfig.builder().build())).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when fingerprinting is on and nothing is tuned should say so")
    void enabledAndEmpty_whenFingerprintingIsOnAndNothingIsTuned_shouldSaySo() {
        assertThat(FingerprintConfig.builder().build().enabledAndEmpty()).isTrue();
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when the library defaults were taken should not report an empty config")
    void enabledAndEmpty_whenLibraryDefaultsWereTaken_shouldNotReportEmptyConfig() {
        assertThat(FingerprintConfig.defaults().enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT enabled() when the flag is null should throw NullPointerException")
    void enabled_whenFlagIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().enabled(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("enabled cannot be null");
    }

    @Test
    @DisplayName("UT fingerprintPolicy() when the policy is null should throw NullPointerException")
    void fingerprintPolicy_whenPolicyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().fingerprintPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintPolicy cannot be null");
    }

    @Test
    @DisplayName("UT bodyHandleStrategy() when the strategy is null should throw NullPointerException")
    void bodyHandleStrategy_whenStrategyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().bodyHandleStrategy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyHandleStrategy cannot be null");
    }

    @Test
    @DisplayName("UT emptyBodyFallback() when the fallback is null should throw NullPointerException")
    void emptyBodyFallback_whenFallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().emptyBodyFallback(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("emptyBodyFallback cannot be null");
    }

    @Test
    @DisplayName("UT bodyCanonicalizer() when the canonicalizer is null should throw NullPointerException")
    void bodyCanonicalizer_whenCanonicalizerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().bodyCanonicalizer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyCanonicalizer cannot be null");
    }

    @Test
    @DisplayName("UT bodyCanonicalizerConfig() when the config is null should throw NullPointerException")
    void bodyCanonicalizerConfig_whenConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FingerprintConfig.builder().bodyCanonicalizerConfig((BodyCanonicalizerConfig) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyCanonicalizerConfig cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target repeats the canonicalizing strategy should keep the canonicalizer the reference tuned")
    void merge_whenTargetRepeatsCanonicalizingStrategy_shouldKeepCanonicalizerReferenceTuned() {
        // given
        BodyCanonicalizer referenceCanonicalizer = canonicalizer();
        FingerprintConfig reference = FingerprintConfig.builder()
                .bodyCanonicalizer(referenceCanonicalizer)
                .build();
        FingerprintConfig target = FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.CANONICALIZED_BODY_HASH)
                .build();

        // when
        FingerprintConfig result = FingerprintConfig.merge(reference, target);

        // then
        assertThat(result.getBodyCanonicalizer()).isSameAs(referenceCanonicalizer);
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        FingerprintConfig config = FingerprintConfig.defaults();

        // when
        FingerprintConfig copy = FingerprintConfig.builder(config)
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build())
                .build();

        // then
        assertThat(copy.getBodyCanonicalizerConfig().getFormat()).isEqualTo(BodyFormat.XML);
        assertThat(config.getBodyCanonicalizerConfig()).isEqualTo(BodyCanonicalizerConfig.defaults());
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when a policy of its own was handed over should not report an empty config")
    void enabledAndEmpty_whenPolicyOfItsOwnWasHandedOver_shouldNotReportEmptyConfig() {
        assertThat(FingerprintConfig.builder().fingerprintPolicy(policy()).build().enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when a canonicalizer of its own was handed over should not report an empty config")
    void enabledAndEmpty_whenCanonicalizerOfItsOwnWasHandedOver_shouldNotReportEmptyConfig() {
        assertThat(FingerprintConfig.builder().bodyCanonicalizer(canonicalizer()).build().enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT enabledAndEmpty() when only the canonicalizer was tuned should not report an empty config")
    void enabledAndEmpty_whenOnlyCanonicalizerWasTuned_shouldNotReportEmptyConfig() {
        assertThat(FingerprintConfig.builder()
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults())
                .build()
                .enabledAndEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT equals() when only the canonicalizer settings differ should tell the configs apart")
    void equals_whenOnlyCanonicalizerSettingsDiffer_shouldTellConfigsApart() {
        assertThat(FingerprintConfig.builder()
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults())
                .build())
                .isNotEqualTo(FingerprintConfig.builder()
                        .bodyCanonicalizerConfig(BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build())
                        .build());
    }

    @Test
    @DisplayName("UT build() when disabled carries a strategy should throw IllegalStateException naming it")
    void build_whenDisabledCarriesStrategy_shouldThrowIllegalStateExceptionNamingIt() {
        assertThatThrownBy(() -> FingerprintConfig.builder()
                .enabled(false)
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bodyHandleStrategy");
    }

    @Test
    @DisplayName("UT build() when disabled carries a canonicalizer should throw IllegalStateException naming it")
    void build_whenDisabledCarriesCanonicalizer_shouldThrowIllegalStateExceptionNamingIt() {
        assertThatThrownBy(() -> FingerprintConfig.builder()
                .enabled(false)
                .bodyCanonicalizer(canonicalizer())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bodyCanonicalizer");
    }

    @Test
    @DisplayName("UT build() when disabled carries canonicalizer settings should throw IllegalStateException naming them")
    void build_whenDisabledCarriesCanonicalizerSettings_shouldThrowIllegalStateExceptionNamingThem() {
        assertThatThrownBy(() -> FingerprintConfig.builder()
                .enabled(false)
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bodyCanonicalizerConfig");
    }

    @Test
    @DisplayName("UT build() when disabled carries every setting should name them all")
    void build_whenDisabledCarriesEverySetting_shouldNameThemAll() {
        assertThatThrownBy(() -> FingerprintConfig.builder(FingerprintConfig.defaults())
                .enabled(false)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bodyHandleStrategy")
                .hasMessageContaining("emptyBodyFallback")
                .hasMessageContaining("bodyCanonicalizerConfig");
    }

    private static FingerprintPolicy policy() {
        return new TestFingerprintPolicy();
    }

    private static BodyCanonicalizer canonicalizer() {
        return body -> new String(body, StandardCharsets.UTF_8);
    }

    private static final class TestFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "fingerprint";
        }

        @Override
        public boolean match(String previous, String current) {
            return true;
        }

        @Override
        public void handle(FingerprintMismatchContext context) {
            // nothing to do
        }
    }
}
