package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.EmptyRequestBodyException;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.starter.FingerprintProperties.BodyCanonicalizerProperties;
import io.github.dmitriyiliyov.idempify.starter.FingerprintProperties.EmptyBodyFallbackStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FingerprintPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new FingerprintProperties(
                null, BodyHandleStrategy.CANONICALIZED_BODY_HASH, EmptyBodyFallbackStrategy.THROWING, canonicalizer()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when strategy is null should throw NullPointerException")
    void constructor_whenStrategyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new FingerprintProperties(
                true, null, EmptyBodyFallbackStrategy.THROWING, canonicalizer()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("strategy cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when emptyBodyFallback is null should throw NullPointerException")
    void constructor_whenEmptyBodyFallbackIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new FingerprintProperties(
                true, BodyHandleStrategy.CANONICALIZED_BODY_HASH, null, canonicalizer()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("emptyBodyFallback cannot be null");
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the canonicalizer block is absent should canonicalize the library's way")
    void toFingerprintConfig_whenCanonicalizerBlockIsAbsent_shouldCanonicalizeLibrarysWay() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true, BodyHandleStrategy.CANONICALIZED_BODY_HASH, EmptyBodyFallbackStrategy.THROWING, null
        );

        // when
        FingerprintConfig result = tested.toFingerprintConfig();

        // then
        assertThat(result.getBodyCanonicalizerConfig()).isEqualTo(BodyCanonicalizerConfig.defaults());
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the canonicalizer block is written for a byte strategy should refuse naming the strategy")
    void toFingerprintConfig_whenCanonicalizerBlockIsWrittenForByteStrategy_shouldRefuseNamingStrategy() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true, BodyHandleStrategy.RAW_BYTES_HASH, EmptyBodyFallbackStrategy.THROWING, canonicalizer()
        );

        // when / then
        assertThatThrownBy(tested::toFingerprintConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("canonicalizer should not be specified when strategy is not CANONICALIZED_BODY_HASH");
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the body is canonicalized should carry the strategy and the canonicalizer block")
    void toFingerprintConfig_whenBodyIsCanonicalized_shouldCarryStrategyAndCanonicalizerBlock() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true,
                BodyHandleStrategy.CANONICALIZED_BODY_HASH,
                EmptyBodyFallbackStrategy.THROWING,
                canonicalizer()
        );

        // when
        FingerprintConfig result = tested.toFingerprintConfig();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(result.getBodyCanonicalizerConfig()).isEqualTo(BodyCanonicalizerConfig.defaults());
        assertThat(result.getEmptyBodyFallback()).isNotNull();
        assertThat(result.getFingerprintPolicy())
                .describedAs("a policy is an instance and cannot be named in YAML")
                .isNull();
        assertThat(result.getBodyCanonicalizer())
                .describedAs("a canonicalizer is an instance and cannot be named in YAML")
                .isNull();
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the body is hashed as bytes should not send settings nobody can read")
    void toFingerprintConfig_whenBodyIsHashedAsBytes_shouldNotSendSettingsNobodyCanRead() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true,
                BodyHandleStrategy.RAW_BYTES_HASH,
                EmptyBodyFallbackStrategy.THROWING,
                null
        );

        // when
        FingerprintConfig result = tested.toFingerprintConfig();

        // then
        assertThat(result.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.RAW_BYTES_HASH);
        assertThat(result.getBodyCanonicalizerConfig())
                .describedAs("canonicalizer settings apply to CANONICALIZED_BODY_HASH only")
                .isNull();
    }

    @Test
    @DisplayName("UT toFingerprintConfig() whatever the strategy should produce a config the core accepts")
    void toFingerprintConfig_whateverTheStrategy_shouldProduceConfigCoreAccepts() {
        // given / when / then
        for (BodyHandleStrategy strategy : BodyHandleStrategy.values()) {
            FingerprintProperties tested = new FingerprintProperties(
                    true, strategy, EmptyBodyFallbackStrategy.THROWING, null
            );

            assertThat(tested.toFingerprintConfig().getBodyHandleStrategy())
                    .describedAs("every value of idempify.fingerprint.strategy is a configuration, not a failure")
                    .isEqualTo(strategy);
        }
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when fingerprinting is switched off should leave nothing behind")
    void toFingerprintConfig_whenFingerprintingIsSwitchedOff_shouldLeaveNothingBehind() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                false,
                BodyHandleStrategy.CANONICALIZED_BODY_HASH,
                EmptyBodyFallbackStrategy.THROWING,
                canonicalizer()
        );

        // when
        FingerprintConfig result = tested.toFingerprintConfig();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getBodyHandleStrategy()).isNull();
        assertThat(result.getEmptyBodyFallback()).isNull();
        assertThat(result.getBodyCanonicalizerConfig()).isNull();
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the fallback is THROWING should reject a request with no body")
    void toFingerprintConfig_whenFallbackIsThrowing_shouldRejectRequestWithNoBody() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true,
                BodyHandleStrategy.CANONICALIZED_BODY_HASH,
                EmptyBodyFallbackStrategy.THROWING,
                canonicalizer()
        );

        // when / then
        assertThatThrownBy(() -> tested.toFingerprintConfig().getEmptyBodyFallback().fallback(mock(RequestContext.class)))
                .isInstanceOf(EmptyRequestBodyException.class);
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the fallback is NOOP should leave a missing body out of the fingerprint")
    void toFingerprintConfig_whenFallbackIsNoop_shouldLeaveMissingBodyOutOfFingerprint() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true,
                BodyHandleStrategy.CANONICALIZED_BODY_HASH,
                EmptyBodyFallbackStrategy.NOOP,
                canonicalizer()
        );

        // when
        byte [] result = tested.toFingerprintConfig().getEmptyBodyFallback().fallback(mock(RequestContext.class));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("UT toFingerprintConfig() when the canonicalizer block names fields should hand them to the core")
    void toFingerprintConfig_whenCanonicalizerBlockNamesFields_shouldHandThemToCore() {
        // given
        BodyCanonicalizerProperties canonicalizer = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of("amount"), Set.of()
        );
        FingerprintProperties tested = new FingerprintProperties(
                true, BodyHandleStrategy.CANONICALIZED_BODY_HASH, EmptyBodyFallbackStrategy.THROWING, canonicalizer
        );

        // when
        BodyCanonicalizerConfig result = tested.toFingerprintConfig().getBodyCanonicalizerConfig();

        // then
        assertThat(result.getIncludedFields()).containsExactly("amount");
        assertThat(result.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // given
        BodyCanonicalizerProperties canonicalizer = canonicalizer();

        // when
        FingerprintProperties tested = new FingerprintProperties(
                true, BodyHandleStrategy.CANONICALIZED_BODY_HASH, EmptyBodyFallbackStrategy.NOOP, canonicalizer
        );

        // then
        assertThat(tested.isEnabled()).isTrue();
        assertThat(tested.getStrategy()).isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        assertThat(tested.getEmptyBodyFallback()).isEqualTo(EmptyBodyFallbackStrategy.NOOP);
        assertThat(tested.getCanonicalizer()).isSameAs(canonicalizer);
    }

    @Test
    @DisplayName("UT toString() should name every property it carries")
    void toString_shouldNameEveryPropertyItCarries() {
        // given
        FingerprintProperties tested = new FingerprintProperties(
                true, BodyHandleStrategy.CANONICALIZED_BODY_HASH, EmptyBodyFallbackStrategy.THROWING, canonicalizer()
        );

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains(
                "enabled=true",
                "strategy=CANONICALIZED_BODY_HASH",
                "emptyBodyFallback=THROWING",
                "canonicalizer=BodyCanonicalizerProperties{"
        );
    }

    private static BodyCanonicalizerProperties canonicalizer() {
        return new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), Set.of()
        );
    }
}
