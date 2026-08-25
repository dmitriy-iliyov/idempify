package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Written from the contract of {@link FingerprintPolicyProvider}: it is the one place that reduces any level
 * of control the config expresses - a ready-made policy, a built-in strategy, a canonicalizer, or only
 * canonicalization settings - to a single policy, and it answers {@code null} when the call site does not
 * fingerprint at all. A section that is switched off is such an answer; a section that is missing is not one,
 * since every layer able to reach this provider owes a decided section.
 */
class DefaultFingerprintPolicyProviderUnitTest {

    @Test
    @DisplayName("UT constructor when bodyCanonicalizerProvider is null should throw NullPointerException")
    void constructor_whenBodyCanonicalizerProviderIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultFingerprintPolicyProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyCanonicalizerProvider cannot be null");
    }

    @Test
    @DisplayName("UT provide() when fingerprinting is off should answer that this call site does not fingerprint")
    void provide_whenFingerprintingIsOff_shouldAnswerThatCallSiteDoesNotFingerprint() {
        // when
        FingerprintPolicy result = tested().provide(FingerprintConfig.disabled());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT provide() when there is no fingerprint section at all should throw NullPointerException")
    void provide_whenThereIsNoFingerprintSectionAtAll_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> tested().provide(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("UT provide() when the config hands over a ready-made policy should give that very instance")
    void provide_whenConfigHandsOverReadyMadePolicy_shouldGiveThatVeryInstance() {
        // given
        FingerprintPolicy policy = new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());

        // when
        FingerprintPolicy result = tested().provide(FingerprintConfig.builder().fingerprintPolicy(policy).build());

        // then
        assertThat(result).isSameAs(policy);
    }

    @Test
    @DisplayName("UT provide() when the strategy is RAW_BYTES_HASH should give the policy that hashes the bytes as they arrived")
    void provide_whenStrategyIsRawBytesHash_shouldGivePolicyThatHashesBytesAsTheyArrived() {
        // when
        FingerprintPolicy result = tested().provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build());

        // then
        assertThat(result).isInstanceOf(RawHashingFingerprintPolicy.class);
    }

    @Test
    @DisplayName("UT provide() when the strategy is NORMALIZED_BYTES_HASH should give the policy that normalizes the text first")
    void provide_whenStrategyIsNormalizedBytesHash_shouldGivePolicyThatNormalizesTextFirst() {
        // when
        FingerprintPolicy result = tested().provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyHandleStrategy(BodyHandleStrategy.NORMALIZED_BYTES_HASH)
                .build());

        // then
        assertThat(result).isInstanceOf(BytesNormalizingFingerprintPolicy.class);
    }

    @Test
    @DisplayName("UT provide() when the strategy is CANONICALIZED_BODY_HASH should give the canonicalizing policy")
    void provide_whenStrategyIsCanonicalizedBodyHash_shouldGiveCanonicalizingPolicy() {
        // when
        FingerprintPolicy result = tested().provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyHandleStrategy(BodyHandleStrategy.CANONICALIZED_BODY_HASH)
                .bodyCanonicalizerConfig(BodyCanonicalizerConfig.defaults())
                .build());

        // then
        assertThat(result).isInstanceOf(BodyCanonicalizingFingerprintPolicy.class);
    }

    @Test
    @DisplayName("UT provide() when canonicalizing is asked for with nothing to canonicalize by should build no policy")
    void provide_whenCanonicalizingIsAskedForWithNothingToCanonicalizeBy_shouldBuildNoPolicy() {
        assertThat(tested().provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyHandleStrategy(BodyHandleStrategy.CANONICALIZED_BODY_HASH)
                .build())).isNull();
    }

    @Test
    @DisplayName("UT provide() when the config tunes canonicalization should build the canonicalizer from those settings")
    void provide_whenConfigTunesCanonicalization_shouldBuildCanonicalizerFromThoseSettings() {
        // given
        RecordingCanonicalizerProvider canonicalizerProvider = new RecordingCanonicalizerProvider();
        DefaultFingerprintPolicyProvider tested = new DefaultFingerprintPolicyProvider(canonicalizerProvider);
        BodyCanonicalizerConfig canonicalizerConfig = BodyCanonicalizerConfig.builder()
                .excludedFields("requestedAt")
                .build();

        // when
        tested.provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyCanonicalizerConfig(canonicalizerConfig)
                .build());

        // then
        assertThat(canonicalizerProvider.lastConfig).isEqualTo(canonicalizerConfig);
    }

    @Test
    @DisplayName("UT provide() when the config hands over a canonicalizer should use it instead of building one")
    void provide_whenConfigHandsOverCanonicalizer_shouldUseItInsteadOfBuildingOne() {
        // given
        RecordingCanonicalizerProvider canonicalizerProvider = new RecordingCanonicalizerProvider();
        DefaultFingerprintPolicyProvider tested = new DefaultFingerprintPolicyProvider(canonicalizerProvider);
        BodyCanonicalizer canonicalizer = body -> "canonical";

        // when
        FingerprintPolicy result = tested.provide(FingerprintConfig.builder()
                .emptyBodyFallback(new ThrowingEmptyBodyFallback())
                .bodyCanonicalizer(canonicalizer)
                .build());

        // then
        assertThat(result).isInstanceOf(BodyCanonicalizingFingerprintPolicy.class);
        assertThat(canonicalizerProvider.lastConfig).isNull();
    }

    @Test
    @DisplayName("UT provide() when the config decides nothing should build no policy rather than invent one")
    void provide_whenConfigDecidesNothing_shouldBuildNoPolicyRatherThanInventOne() {
        assertThat(tested().provide(FingerprintConfig.builder().build())).isNull();
    }

    @Test
    @DisplayName("UT provide() when the strategy is named but the empty-body fallback is not should build no policy")
    void provide_whenStrategyIsNamedButEmptyBodyFallbackIsNot_shouldBuildNoPolicy() {
        assertThat(tested().provide(FingerprintConfig.builder()
                .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                .build())).isNull();
    }

    @Test
    @DisplayName("UT provide() when the library's own answers are asked for should build the policy they describe")
    void provide_whenLibrarySOwnAnswersAreAskedFor_shouldBuildPolicyTheyDescribe() {
        assertThat(tested().provide(FingerprintConfig.defaults()))
                .isInstanceOf(BodyCanonicalizingFingerprintPolicy.class);
    }

    private static DefaultFingerprintPolicyProvider tested() {
        return new DefaultFingerprintPolicyProvider(new RecordingCanonicalizerProvider());
    }

    /**
     * Remembers what it was asked for, so a test can tell "built from these settings" from "took the
     * canonicalizer it was handed".
     */
    private static final class RecordingCanonicalizerProvider implements BodyCanonicalizerProvider {

        private BodyCanonicalizerConfig lastConfig;

        @Override
        public BodyCanonicalizer provide(BodyCanonicalizerConfig config) {
            lastConfig = config;
            return body -> new String(body, StandardCharsets.UTF_8);
        }
    }
}
