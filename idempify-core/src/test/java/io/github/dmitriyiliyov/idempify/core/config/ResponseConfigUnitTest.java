package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A partial description: every flag may be left undecided, and merging is what turns a stack of layers into
 * one answer. The tests judge that layering, not the getters.
 */
class ResponseConfigUnitTest {

    @Test
    @DisplayName("UT defaults() should carry the built-in decision about failures")
    void defaults_shouldCarryBuiltInDecisionAboutFailures() {
        // when
        ResponseConfig result = ResponseConfig.defaults();

        // then
        assertThat(result.shouldCache4xx()).isEqualTo(ResponseConfig.DEFAULT_SHOULD_CACHE_4XX);
        assertThat(result.shouldCache5xx()).isEqualTo(ResponseConfig.DEFAULT_SHOULD_CACHE_5XX);
    }

    @Test
    @DisplayName("UT all() should keep every response, failures included")
    void all_shouldKeepEveryResponseFailuresIncluded() {
        // when
        ResponseConfig result = ResponseConfig.all();

        // then
        assertThat(result.shouldCache4xx()).isTrue();
        assertThat(result.shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT disabled() should keep no failure response at all")
    void disabled_shouldKeepNoFailureResponseAtAll() {
        // when
        ResponseConfig result = ResponseConfig.disabled();

        // then
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT builder() when nothing is told should leave every flag undecided")
    void builder_whenNothingIsTold_shouldLeaveEveryFlagUndecided() {
        // when
        ResponseConfig result = ResponseConfig.builder().build();

        // then
        assertThat(result.shouldCache4xx()).isNull();
        assertThat(result.shouldCache5xx()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        ResponseConfig reference = ResponseConfig.all();

        // when
        ResponseConfig result = ResponseConfig.merge(reference, ResponseConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the target drops 4xx should keep 5xx from the reference")
    void merge_whenTargetDrops4xx_shouldKeep5xxFromReference() {
        // given
        ResponseConfig reference = ResponseConfig.all();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().shouldCache4xx(false).build()
        );

        // then
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the target keeps 5xx should take it over the reference")
    void merge_whenTargetKeeps5xx_shouldTakeItOverReference() {
        // given
        ResponseConfig reference = ResponseConfig.disabled();

        // when
        ResponseConfig result = ResponseConfig.merge(
                reference,
                ResponseConfig.builder().shouldCache5xx(true).build()
        );

        // then
        assertThat(result.shouldCache5xx()).isTrue();
        assertThat(result.shouldCache4xx()).isFalse();
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
        assertThatThrownBy(() -> ResponseConfig.merge(null, ResponseConfig.all()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseConfig.merge(ResponseConfig.all(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("target cannot be null");
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        ResponseConfig original = ResponseConfig.all();

        // when
        ResponseConfig copy = ResponseConfig.builder(original).shouldCache4xx(false).build();

        // then
        assertThat(original.shouldCache4xx()).isTrue();
        assertThat(copy.shouldCache4xx()).isFalse();
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
    @DisplayName("UT equals() when every flag matches should be equal and share a hash code")
    void equals_whenEveryFlagMatches_shouldBeEqualAndShareHashCode() {
        // when
        ResponseConfig first = ResponseConfig.all();
        ResponseConfig second = ResponseConfig.all();

        // then
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("UT equals() when only the 5xx flag differs should not be equal")
    void equals_whenOnly5xxFlagDiffers_shouldNotBeEqual() {
        // when / then
        assertThat(ResponseConfig.builder().shouldCache4xx(true).shouldCache5xx(true).build())
                .isNotEqualTo(ResponseConfig.builder().shouldCache4xx(true).shouldCache5xx(false).build());
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
        assertThat(ResponseConfig.all().toString())
                .contains("shouldCache4xx=true", "shouldCache5xx=true");
    }
}
