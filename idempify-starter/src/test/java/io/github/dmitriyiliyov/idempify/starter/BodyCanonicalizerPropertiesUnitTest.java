package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import io.github.dmitriyiliyov.idempify.starter.FingerprintProperties.BodyCanonicalizerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodyCanonicalizerPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when format is null should throw NullPointerException")
    void constructor_whenFormatIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new BodyCanonicalizerProperties(
                null, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), Set.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("format cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when strategy is null should throw NullPointerException")
    void constructor_whenStrategyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new BodyCanonicalizerProperties(BodyFormat.JSON, null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("strategy cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when includedFields is null should throw NullPointerException")
    void constructor_whenIncludedFieldsIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, null, Set.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("includedFields cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when excludedFields is null should throw NullPointerException")
    void constructor_whenExcludedFieldsIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("excludedFields cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when both field sets are named should refuse them the way the core does")
    void constructor_whenBothFieldSetsAreNamed_shouldRefuseThemTheWayCoreDoes() {
        // when / then
        assertThatThrownBy(() -> new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of("amount"), Set.of("timestamp")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("includedFields and excludedFields select the body in opposite ways");
    }

    @Test
    @DisplayName("UT toBodyCanonicalizerConfig() when nothing is restricted should decide format and strategy only")
    void toBodyCanonicalizerConfig_whenNothingIsRestricted_shouldDecideFormatAndStrategyOnly() {
        // given
        BodyCanonicalizerProperties tested = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), Set.of()
        );

        // when
        BodyCanonicalizerConfig result = tested.toBodyCanonicalizerConfig();

        // then
        assertThat(result.getFormat()).isEqualTo(BodyFormat.JSON);
        assertThat(result.getCanonicalizeStrategy()).isEqualTo(CanonicalizeStrategy.LEXICOGRAPHICAL);
        assertThat(result.getIncludedFields()).isEmpty();
        assertThat(result.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT toBodyCanonicalizerConfig() when fields are excluded should carry them")
    void toBodyCanonicalizerConfig_whenFieldsAreExcluded_shouldCarryThem() {
        // given
        BodyCanonicalizerProperties tested = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), Set.of("timestamp")
        );

        // when
        BodyCanonicalizerConfig result = tested.toBodyCanonicalizerConfig();

        // then
        assertThat(result.getExcludedFields()).containsExactly("timestamp");
        assertThat(result.getIncludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT toBodyCanonicalizerConfig() called twice should describe the same canonicalization")
    void toBodyCanonicalizerConfig_calledTwice_shouldDescribeSameCanonicalization() {
        // given
        BodyCanonicalizerProperties tested = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of("amount"), Set.of()
        );

        // when / then
        assertThat(tested.toBodyCanonicalizerConfig()).isEqualTo(tested.toBodyCanonicalizerConfig());
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // given
        BodyCanonicalizerProperties tested = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of("amount"), Set.of()
        );

        // when / then
        assertThat(tested.getFormat()).isEqualTo(BodyFormat.JSON);
        assertThat(tested.getStrategy()).isEqualTo(CanonicalizeStrategy.LEXICOGRAPHICAL);
        assertThat(tested.getIncludedFields()).containsExactly("amount");
        assertThat(tested.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT toString() should name every property it carries")
    void toString_shouldNameEveryPropertyItCarries() {
        // given
        BodyCanonicalizerProperties tested = new BodyCanonicalizerProperties(
                BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of("amount"), Set.of()
        );

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains(
                "format=JSON",
                "strategy=LEXICOGRAPHICAL",
                "includedFields=[amount]",
                "excludedFields=[]"
        );
    }
}
