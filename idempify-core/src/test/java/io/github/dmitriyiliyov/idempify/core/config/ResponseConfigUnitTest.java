package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A partial description: every setting may be left undecided, and merging is what turns a stack of layers into
 * one answer. The tests judge that layering, not the getters.
 */
class ResponseConfigUnitTest {

    @Test
    @DisplayName("UT defaults() should keep no failure of the application")
    void defaults_shouldKeepNoFailureOfApplication() {
        // when
        ResponseConfig result = ResponseConfig.defaults();

        // then
        assertThat(result.getRecordFor()).isEmpty();
    }

    @Test
    @DisplayName("UT builder() when nothing is told should leave every setting undecided")
    void builder_whenNothingIsTold_shouldLeaveEverySettingUndecided() {
        // when
        ResponseConfig result = ResponseConfig.builder().build();

        // then
        assertThat(result.getRecordFor()).isNull();
        assertThat(result.getIncludedHeaders()).isNull();
        assertThat(result.getExcludedHeaders()).isNull();
    }

    @Test
    @DisplayName("UT recordFor() when the list is null should throw NullPointerException")
    void recordFor_whenListIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseConfig.builder().recordFor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recordFor cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        ResponseConfig reference = ResponseConfig.defaults();

        // when
        ResponseConfig result = ResponseConfig.merge(reference, ResponseConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the target names recordFor should take it over the reference")
    void merge_whenTargetNamesRecordFor_shouldTakeItOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.defaults();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().recordFor(List.of(IllegalStateException.class)).build()
        );

        // then
        assertThat(result.getRecordFor()).containsExactly(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT merge() when the target keeps nothing with an empty list should take it over the reference")
    void merge_whenTargetKeepsNothingWithEmptyList_shouldTakeItOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder().recordFor(List.of(Throwable.class)).build();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().recordFor(List.of()).build()
        );

        // then
        assertThat(result.getRecordFor()).isEmpty();
    }

    @Test
    @DisplayName("UT merge() when the target leaves recordFor undecided should keep the one of the reference")
    void merge_whenTargetLeavesRecordForUndecided_shouldKeepOneOfReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder().recordFor(List.of(Throwable.class)).build();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().includedHeaders(Set.of("Location")).build()
        );

        // then
        assertThat(result.getRecordFor()).containsExactly(Throwable.class);
    }

    @Test
    @DisplayName("UT merge() when the target names included headers should take them over the reference")
    void merge_whenTargetNamesIncludedHeaders_shouldTakeThemOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder()
                .includedHeaders(Set.of("ETag"))
                .build();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().includedHeaders(Set.of("Location")).build()
        );

        // then
        assertThat(result.getIncludedHeaders()).containsExactly("location");
    }

    @Test
    @DisplayName("UT merge() when the target names excluded headers should take them over the reference")
    void merge_whenTargetNamesExcludedHeaders_shouldTakeThemOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder()
                .excludedHeaders(Set.of("ETag"))
                .build();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().excludedHeaders(Set.of("Set-Cookie")).build()
        );

        // then
        assertThat(result.getExcludedHeaders()).containsExactly("set-cookie");
    }

    @Test
    @DisplayName("UT merge() when the target names no headers should keep the sets of the reference")
    void merge_whenTargetNamesNoHeaders_shouldKeepSetsOfReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder()
                .includedHeaders(Set.of("Location"))
                .excludedHeaders(Set.of("Set-Cookie"))
                .build();

        // when
        ResponseConfig result = ResponseConfig.merge(reference, ResponseConfig.builder().build());

        // then
        assertThat(result.getIncludedHeaders()).containsExactly("location");
        assertThat(result.getExcludedHeaders()).containsExactly("set-cookie");
    }

    @Test
    @DisplayName("UT merge() when the target keeps every header with an empty set should take it over the reference")
    void merge_whenTargetKeepsEveryHeaderWithEmptySet_shouldTakeItOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.builder()
                .includedHeaders(Set.of("Location"))
                .build();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().includedHeaders(Set.of()).build()
        );

        // then
        assertThat(result.getIncludedHeaders()).isEmpty();
    }

    @Test
    @DisplayName("UT merge() when reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseConfig.merge(null, ResponseConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseConfig.merge(ResponseConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("target cannot be null");
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        ResponseConfig original = ResponseConfig.defaults();

        // when
        ResponseConfig copy = ResponseConfig.builder(original)
                .recordFor(List.of(IllegalStateException.class))
                .build();

        // then
        assertThat(original.getRecordFor()).isEmpty();
        assertThat(copy.getRecordFor()).containsExactly(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT builder(config) when nothing is tuned should carry recordFor over")
    void builderFromConfig_whenNothingIsTuned_shouldCarryRecordForOver() {
        // when
        ResponseConfig copy = ResponseConfig.builder(ResponseConfig.defaults()).build();

        // then
        assertThat(copy).isEqualTo(ResponseConfig.defaults());
    }

    @Test
    @DisplayName("UT getIncludedHeaders() should hand back the named headers lowercased, whatever case named them")
    void getIncludedHeaders_shouldHandBackNamedHeadersLowercased() {
        // when
        ResponseConfig result = ResponseConfig.builder()
                .includedHeaders(Set.of("Location"))
                .excludedHeaders(Set.of("Set-Cookie"))
                .build();

        // then
        assertThat(result.getIncludedHeaders()).containsExactly("location");
        assertThat(result.getExcludedHeaders()).containsExactly("set-cookie");
    }

    @Test
    @DisplayName("UT equals() when every setting matches should be equal and share a hash code")
    void equals_whenEverySettingMatches_shouldBeEqualAndShareHashCode() {
        // when
        ResponseConfig first = ResponseConfig.defaults();
        ResponseConfig second = ResponseConfig.defaults();

        // then
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("UT equals() when only recordFor differs should not be equal")
    void equals_whenOnlyRecordForDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(ResponseConfig.defaults())
                .isNotEqualTo(ResponseConfig.builder().recordFor(List.of(Throwable.class)).build());
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        // when / then
        assertThat(ResponseConfig.defaults()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        // when / then
        assertThat(ResponseConfig.defaults()).isNotEqualTo("ResponseConfig");
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        // given
        ResponseConfig config = ResponseConfig.defaults();

        // when / then
        assertThat(config).isEqualTo(config);
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        // when / then
        assertThat(ResponseConfig.builder().recordFor(List.of(Throwable.class)).build().toString())
                .contains("recordFor=[class java.lang.Throwable]");
    }

    @Test
    @DisplayName("UT equals() when compared with null directly should not be equal")
    void equals_whenComparedWithNullDirectly_shouldNotBeEqual() {
        assertThat(ResponseConfig.defaults().equals(null)).isFalse();
    }

    @Test
    @DisplayName("UT equals() when only the included headers differ should not be equal")
    void equals_whenOnlyIncludedHeadersDiffer_shouldNotBeEqual() {
        assertThat(ResponseConfig.builder().recordFor(List.of(Throwable.class)).includedHeaders(Set.of("ETag")).build())
                .isNotEqualTo(ResponseConfig.builder().recordFor(List.of(Throwable.class)).includedHeaders(Set.of("Location")).build());
    }

    @Test
    @DisplayName("UT equals() when only the excluded headers differ should not be equal")
    void equals_whenOnlyExcludedHeadersDiffer_shouldNotBeEqual() {
        assertThat(ResponseConfig.builder().includedHeaders(Set.of()).excludedHeaders(Set.of("ETag")).build())
                .isNotEqualTo(ResponseConfig.builder().includedHeaders(Set.of()).excludedHeaders(Set.of("Location")).build());
    }
}
