package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodyCanonicalizerConfigUnitTest {

    @Test
    @DisplayName("UT defaults() should pick no fields either way and canonicalize lexicographically")
    void defaults_shouldPickNoFieldsEitherWayAndCanonicalizeLexicographically() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.defaults();

        // then
        assertThat(result.getIncludedFields()).isEmpty();
        assertThat(result.getExcludedFields()).isEmpty();
        assertThat(result.getCanonicalizeStrategy()).isEqualTo(CanonicalizeStrategy.LEXICOGRAPHICAL);
    }

    @Test
    @DisplayName("UT includedFields() should strip the names it is given")
    void includedFields_shouldStripNamesItIsGiven() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .includedFields("  amount  ", "currency")
                .build();

        // then
        assertThat(result.getIncludedFields()).containsExactlyInAnyOrder("amount", "currency");
    }

    @Test
    @DisplayName("UT includedFields() should drop blank names")
    void includedFields_shouldDropBlankNames() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .includedFields(Set.of("amount", "   "))
                .build();

        // then
        assertThat(result.getIncludedFields()).containsExactly("amount");
    }

    @Test
    @DisplayName("UT includedFields() when the names are null should pick no fields")
    void includedFields_whenNamesAreNull_shouldPickNoFields() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .includedFields((Set<String>) null)
                .build();

        // then
        assertThat(result.getIncludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT includedFields() when the name array is null should pick no fields")
    void includedFields_whenNameArrayIsNull_shouldPickNoFields() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .includedFields((String[]) null)
                .build();

        // then
        assertThat(result.getIncludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT excludedFields() when the name set is null should pick no fields")
    void excludedFields_whenNameSetIsNull_shouldPickNoFields() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .excludedFields((Set<String>) null)
                .build();

        // then
        assertThat(result.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT excludedFields() should drop blank names")
    void excludedFields_shouldDropBlankNames() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .excludedFields(Set.of("requestedAt", "  "))
                .build();

        // then
        assertThat(result.getExcludedFields()).containsExactly("requestedAt");
    }

    @Test
    @DisplayName("UT excludedFields() should strip the names it is given")
    void excludedFields_shouldStripNamesItIsGiven() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .excludedFields("  requestedAt  ")
                .build();

        // then
        assertThat(result.getExcludedFields()).containsExactly("requestedAt");
    }

    @Test
    @DisplayName("UT excludedFields() when the names are null should pick no fields")
    void excludedFields_whenNamesAreNull_shouldPickNoFields() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .excludedFields((String[]) null)
                .build();

        // then
        assertThat(result.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT getIncludedFields() should hand back a set nobody can add to")
    void getIncludedFields_shouldHandBackSetNobodyCanAddTo() {
        // given
        BodyCanonicalizerConfig tested = BodyCanonicalizerConfig.builder().includedFields("amount").build();

        // when / then
        assertThatThrownBy(() -> tested.getIncludedFields().add("currency"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UT build() when both an allow list and a deny list are given should throw IllegalStateException")
    void build_whenBothAllowListAndDenyListAreGiven_shouldThrowIllegalStateException() {
        assertThatThrownBy(() -> BodyCanonicalizerConfig.builder()
                .includedFields("amount")
                .excludedFields("requestedAt")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("includedFields")
                .hasMessageContaining("excludedFields");
    }

    @Test
    @DisplayName("UT canonicalizeStrategy() when the strategy is null should throw NullPointerException")
    void canonicalizeStrategy_whenStrategyIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> BodyCanonicalizerConfig.builder().canonicalizeStrategy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("canonicalizeStrategy cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target names another format should take it")
    void merge_whenTargetNamesAnotherFormat_shouldTakeIt() {
        // given
        BodyCanonicalizerConfig target = BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build();

        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.merge(BodyCanonicalizerConfig.defaults(), target);

        // then
        assertThat(result.getFormat()).isEqualTo(BodyFormat.XML);
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        BodyCanonicalizerConfig reference = BodyCanonicalizerConfig.builder()
                .format(BodyFormat.PROTOBUF)
                .excludedFields("requestedAt")
                .build();

        // when
        BodyCanonicalizerConfig result =
                BodyCanonicalizerConfig.merge(reference, BodyCanonicalizerConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the target excludes other fields should replace the reference deny list")
    void merge_whenTargetExcludesOtherFields_shouldReplaceReferenceDenyList() {
        // given
        BodyCanonicalizerConfig reference = BodyCanonicalizerConfig.builder().excludedFields("requestedAt").build();
        BodyCanonicalizerConfig target = BodyCanonicalizerConfig.builder().excludedFields("clientTime").build();

        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.merge(reference, target);

        // then
        assertThat(result.getExcludedFields()).containsExactly("clientTime");
    }

    @Test
    @DisplayName("UT merge() when the target picks fields the reference excluded should replace both lists")
    void merge_whenTargetPicksFieldsReferenceExcluded_shouldReplaceBothLists() {
        // given
        BodyCanonicalizerConfig reference = BodyCanonicalizerConfig.builder().excludedFields("requestedAt").build();
        BodyCanonicalizerConfig target = BodyCanonicalizerConfig.builder().includedFields("amount").build();

        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.merge(reference, target);

        // then
        assertThat(result.getIncludedFields()).containsExactly("amount");
        assertThat(result.getExcludedFields()).isEmpty();
    }

    @Test
    @DisplayName("UT merge() when the target names no fields should keep the reference field preference")
    void merge_whenTargetNamesNoFields_shouldKeepReferenceFieldPreference() {
        // given
        BodyCanonicalizerConfig reference = BodyCanonicalizerConfig.builder().includedFields("amount").build();
        BodyCanonicalizerConfig target = BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build();

        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.merge(reference, target);

        // then
        assertThat(result.getIncludedFields()).containsExactly("amount");
        assertThat(result.getFormat()).isEqualTo(BodyFormat.XML);
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> BodyCanonicalizerConfig.merge(null, BodyCanonicalizerConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> BodyCanonicalizerConfig.merge(BodyCanonicalizerConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT equals() when the same fields are named in another order should be equal and share the hash code")
    void equals_whenSameFieldsAreNamedInAnotherOrder_shouldBeEqualAndShareHashCode() {
        // given
        BodyCanonicalizerConfig one = BodyCanonicalizerConfig.builder().includedFields("amount", "currency").build();
        BodyCanonicalizerConfig other = BodyCanonicalizerConfig.builder().includedFields("currency", "amount").build();

        // when / then
        assertThat(one).isEqualTo(other).isNotSameAs(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the same names sit in the other list should not be equal")
    void equals_whenSameNamesSitInOtherList_shouldNotBeEqual() {
        // given
        BodyCanonicalizerConfig one = BodyCanonicalizerConfig.builder().includedFields("amount").build();
        BodyCanonicalizerConfig other = BodyCanonicalizerConfig.builder().excludedFields("amount").build();

        // when / then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when the format differs should not be equal")
    void equals_whenFormatDiffers_shouldNotBeEqual() {
        // given
        BodyCanonicalizerConfig other = BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build();

        // when / then
        assertThat(BodyCanonicalizerConfig.defaults()).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT toString() should name the fields and the strategy it carries")
    void toString_shouldNameFieldsAndStrategyItCarries() {
        assertThat(BodyCanonicalizerConfig.builder(BodyCanonicalizerConfig.defaults())
                .includedFields("amount")
                .build()
                .toString())
                .contains("includedFields=[amount]", "excludedFields=[]", "canonicalizeStrategy=LEXICOGRAPHICAL");
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(BodyCanonicalizerConfig.defaults()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(BodyCanonicalizerConfig.defaults()).isNotEqualTo("JSON");
    }

    @Test
    @DisplayName("UT canonicalizeStrategy() should carry the ordering it was given")
    void canonicalizeStrategy_shouldCarryOrderingItWasGiven() {
        // when
        BodyCanonicalizerConfig result = BodyCanonicalizerConfig.builder()
                .canonicalizeStrategy(CanonicalizeStrategy.LEXICOGRAPHICAL)
                .build();

        // then
        assertThat(result.getCanonicalizeStrategy()).isEqualTo(CanonicalizeStrategy.LEXICOGRAPHICAL);
    }

    @Test
    @DisplayName("UT equals() when the formats differ should tell the configs apart")
    void equals_whenFormatsDiffer_shouldTellConfigsApart() {
        assertThat(BodyCanonicalizerConfig.builder().format(BodyFormat.JSON).build())
                .isNotEqualTo(BodyCanonicalizerConfig.builder().format(BodyFormat.XML).build());
    }

    @Test
    @DisplayName("UT equals() when the excluded fields differ should tell the configs apart")
    void equals_whenExcludedFieldsDiffer_shouldTellConfigsApart() {
        assertThat(BodyCanonicalizerConfig.builder().excludedFields("a").build())
                .isNotEqualTo(BodyCanonicalizerConfig.builder().excludedFields("b").build());
    }

    @Test
    @DisplayName("UT format() when the format is null should throw NullPointerException")
    void format_whenFormatIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> BodyCanonicalizerConfig.builder().format(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("format cannot be null");
    }

    @Test
    @DisplayName("UT getExcludedFields() should hand back a set nobody can add to")
    void getExcludedFields_shouldHandBackSetNobodyCanAddTo() {
        // given
        BodyCanonicalizerConfig tested = BodyCanonicalizerConfig.builder().excludedFields("requestedAt").build();

        // when / then
        assertThatThrownBy(() -> tested.getExcludedFields().add("clientId"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        BodyCanonicalizerConfig config = BodyCanonicalizerConfig.defaults();

        // when
        BodyCanonicalizerConfig copy = BodyCanonicalizerConfig.builder(config).format(BodyFormat.XML).build();

        // then
        assertThat(copy.getFormat()).isEqualTo(BodyFormat.XML);
        assertThat(config.getFormat()).isEqualTo(BodyFormat.JSON);
    }

    @Test
    @DisplayName("UT hashCode() when two configs are equal should agree")
    void hashCode_whenTwoConfigsAreEqual_shouldAgree() {
        assertThat(BodyCanonicalizerConfig.defaults()).hasSameHashCodeAs(BodyCanonicalizerConfig.builder()
                .format(BodyFormat.JSON)
                .canonicalizeStrategy(CanonicalizeStrategy.LEXICOGRAPHICAL)
                .build());
    }

    @Test
    @DisplayName("UT equals() when one side leaves the ordering to the layer below should tell the configs apart")
    void equals_whenOneSideLeavesOrderingToLayerBelow_shouldTellConfigsApart() {
        assertThat(BodyCanonicalizerConfig.builder()
                .format(BodyFormat.JSON)
                .canonicalizeStrategy(CanonicalizeStrategy.LEXICOGRAPHICAL)
                .build())
                .isNotEqualTo(BodyCanonicalizerConfig.builder()
                        .format(BodyFormat.JSON)
                        .build());
    }
}
