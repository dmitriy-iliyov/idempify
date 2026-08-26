package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCacheConfigUnitTest {

    @Test
    @DisplayName("UT defaults() should cache nothing until an application asks for it")
    void defaults_shouldCacheNothingUntilApplicationAsksForIt() {
        // when
        ResponseCacheConfig result = ResponseCacheConfig.defaults();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT all() should cache every response, failures included")
    void all_shouldCacheEveryResponseFailuresIncluded() {
        // when
        ResponseCacheConfig result = ResponseCacheConfig.all();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.shouldCache4xx()).isTrue();
        assertThat(result.shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT disabled() should cache nothing at all")
    void disabled_shouldCacheNothingAtAll() {
        // when
        ResponseCacheConfig result = ResponseCacheConfig.disabled();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT build() when caching is off but failures are kept should still report caching off")
    void build_whenCachingIsOffButFailuresAreKept_shouldStillReportCachingOff() {
        // when
        ResponseCacheConfig result = ResponseCacheConfig.builder()
                .enabled(false)
                .shouldCache4xx(true)
                .build();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.shouldCache4xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the target caches nothing should cache nothing at all")
    void merge_whenTargetCachesNothing_shouldCacheNothingAtAll() {
        // when
        ResponseCacheConfig result = ResponseCacheConfig.merge(ResponseCacheConfig.all(), ResponseCacheConfig.disabled());

        // then
        assertThat(result).isEqualTo(ResponseCacheConfig.disabled());
    }

    @Test
    @DisplayName("UT merge() when the target decides nothing should keep the reference untouched")
    void merge_whenTargetDecidesNothing_shouldKeepReferenceUntouched() {
        // given
        ResponseCacheConfig reference = ResponseCacheConfig.all();

        // when
        ResponseCacheConfig result = ResponseCacheConfig.merge(reference, ResponseCacheConfig.builder().build());

        // then
        assertThat(result).isEqualTo(reference);
    }

    @Test
    @DisplayName("UT merge() when the reference caches nothing should stay off however the target asks")
    void merge_whenReferenceCachesNothing_shouldStayOffHoweverTargetAsks() {
        // when / then
        assertThat(ResponseCacheConfig.merge(
                ResponseCacheConfig.disabled(),
                ResponseCacheConfig.builder().enabled(true).build()
        )).isEqualTo(ResponseCacheConfig.disabled());

        assertThat(ResponseCacheConfig.merge(
                ResponseCacheConfig.disabled(),
                ResponseCacheConfig.builder().build()
        )).isEqualTo(ResponseCacheConfig.disabled());
    }

    @Test
    @DisplayName("UT merge() when the target asks for the defaults should override the reference with them")
    void merge_whenTargetAsksForDefaults_shouldOverrideReferenceWithThem() {
        // when
        ResponseCacheConfig result =
                ResponseCacheConfig.merge(ResponseCacheConfig.all(), ResponseCacheConfig.defaults());

        // then
        assertThat(result).isEqualTo(ResponseCacheConfig.defaults());
    }

    @Test
    @DisplayName("UT merge() when the target drops 4xx should keep 5xx from the reference")
    void merge_whenTargetDrops4xx_shouldKeep5xxFromReference() {
        // given
        ResponseCacheConfig target = ResponseCacheConfig.builder().shouldCache4xx(false).build();

        // when
        ResponseCacheConfig result = ResponseCacheConfig.merge(ResponseCacheConfig.all(), target);

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the target keeps 5xx should take it over the reference")
    void merge_whenTargetKeeps5xx_shouldTakeItOverReference() {
        // given
        ResponseCacheConfig reference = ResponseCacheConfig.builder()
                .enabled(true)
                .shouldCache4xx(true)
                .shouldCache5xx(false)
                .build();
        ResponseCacheConfig target = ResponseCacheConfig.builder().shouldCache5xx(true).build();

        // when
        ResponseCacheConfig result = ResponseCacheConfig.merge(reference, target);

        // then
        assertThat(result).isEqualTo(ResponseCacheConfig.all());
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ResponseCacheConfig.merge(null, ResponseCacheConfig.defaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> ResponseCacheConfig.merge(ResponseCacheConfig.defaults(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT equals() when every flag matches should be equal and share the hash code")
    void equals_whenEveryFlagMatches_shouldBeEqualAndShareHashCode() {
        // given
        ResponseCacheConfig one = ResponseCacheConfig.defaults();
        ResponseCacheConfig other = ResponseCacheConfig.defaults();

        // when / then
        assertThat(one).isEqualTo(other).isNotSameAs(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when one flag differs should not be equal")
    void equals_whenOneFlagDiffers_shouldNotBeEqual() {
        assertThat(ResponseCacheConfig.defaults()).isNotEqualTo(ResponseCacheConfig.all());
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        assertThat(ResponseCacheConfig.all().toString())
                .contains("enabled=true", "shouldCache4xx=true", "shouldCache5xx=true");
    }

    @Test
    @DisplayName("UT validate() when caching is off but 4xx is kept should throw IllegalStateException")
    void validate_whenCachingIsOffBut4xxIsKept_shouldThrowIllegalStateException() {
        // given
        ResponseCacheConfig config = ResponseCacheConfig.builder()
                .enabled(false)
                .shouldCache4xx(true)
                .shouldCache5xx(false)
                .build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("any properties shouldn't be enabled when responseCache is disabled");
    }

    @Test
    @DisplayName("UT validate() when caching is off but 5xx is kept should throw IllegalStateException")
    void validate_whenCachingIsOffBut5xxIsKept_shouldThrowIllegalStateException() {
        // given
        ResponseCacheConfig config = ResponseCacheConfig.builder()
                .enabled(false)
                .shouldCache4xx(false)
                .shouldCache5xx(true)
                .build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("any properties shouldn't be enabled when responseCache is disabled");
    }

    @Test
    @DisplayName("UT validate() when caching is off and nothing is kept should accept it")
    void validate_whenCachingIsOffAndNothingIsKept_shouldAcceptIt() {
        ResponseCacheConfig.disabled().validate();
    }

    @Test
    @DisplayName("UT validate() when caching is on should judge no flag at all")
    void validate_whenCachingIsOn_shouldJudgeNoFlagAtAll() {
        ResponseCacheConfig.all().validate();
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        ResponseCacheConfig config = ResponseCacheConfig.defaults();

        // when
        ResponseCacheConfig copy = ResponseCacheConfig.builder(config).shouldCache5xx(true).build();

        // then
        assertThat(copy.shouldCache5xx()).isTrue();
        assertThat(config.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(ResponseCacheConfig.defaults()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(ResponseCacheConfig.defaults()).isNotEqualTo("ResponseCacheConfig");
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        // given
        ResponseCacheConfig config = ResponseCacheConfig.defaults();

        // when / then
        assertThat(config).isEqualTo(config);
    }

    @Test
    @DisplayName("UT equals() when only the 5xx flag differs should not be equal")
    void equals_whenOnly5xxFlagDiffers_shouldNotBeEqual() {
        assertThat(ResponseCacheConfig.builder().enabled(true).shouldCache4xx(true).shouldCache5xx(true).build())
                .isNotEqualTo(ResponseCacheConfig.builder().enabled(true).shouldCache4xx(true).shouldCache5xx(false).build());
    }

    @Test
    @DisplayName("UT equals() when only the switch differs should not be equal")
    void equals_whenOnlySwitchDiffers_shouldNotBeEqual() {
        assertThat(ResponseCacheConfig.disabled())
                .isNotEqualTo(ResponseCacheConfig.builder()
                        .enabled(true)
                        .shouldCache4xx(false)
                        .shouldCache5xx(false)
                        .build());
    }
}
