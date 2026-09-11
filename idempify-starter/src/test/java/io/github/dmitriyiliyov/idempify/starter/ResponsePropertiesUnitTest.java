package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The block hands the core a fully decided response policy, so what is judged here is that every setting
 * survives the trip and that a block nobody wrote still answers.
 */
class ResponsePropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when shouldCache4xx is null should throw NullPointerException")
    void constructor_whenShouldCache4xxIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(null, false, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("shouldCache4xx cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when shouldCache5xx is null should throw NullPointerException")
    void constructor_whenShouldCache5xxIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(false, null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("shouldCache5xx cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when includedHeaders is null should throw NullPointerException")
    void constructor_whenIncludedHeadersIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(false, false, null, Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("includedHeaders cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when excludedHeaders is null should throw NullPointerException")
    void constructor_whenExcludedHeadersIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(false, false, Set.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("excludedHeaders cannot be null");
    }

    @Test
    @DisplayName("UT toResponseConfig() should carry what the application decided about failure answers")
    void toResponseConfig_shouldCarryWhatApplicationDecidedAboutFailureAnswers() {
        // given
        ResponseProperties tested = new ResponseProperties(true, false, Set.of(), Set.of());

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.shouldCache4xx()).isTrue();
        assertThat(result.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT toResponseConfig() when headers are named should carry both sets lowercased")
    void toResponseConfig_whenHeadersAreNamed_shouldCarryBothSetsLowercased() {
        // given
        ResponseProperties tested = new ResponseProperties(
                false, false, Set.of("Location", "ETag"), Set.of("Set-Cookie"));

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.getIncludedHeaders()).containsExactlyInAnyOrder("location", "etag");
        assertThat(result.getExcludedHeaders()).containsExactly("set-cookie");
    }

    @Test
    @DisplayName("UT toResponseConfig() when no header is named should narrow nothing")
    void toResponseConfig_whenNoHeaderIsNamed_shouldNarrowNothing() {
        // given
        ResponseProperties tested = new ResponseProperties(false, false, Set.of(), Set.of());

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.getIncludedHeaders()).isEmpty();
        assertThat(result.getExcludedHeaders()).isEmpty();
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // when
        ResponseProperties result = new ResponseProperties(
                true, true, Set.of("Location"), Set.of("Set-Cookie"));

        // then
        assertThat(result.getShouldCache4xx()).isTrue();
        assertThat(result.getShouldCache5xx()).isTrue();
        assertThat(result.getIncludedHeaders()).containsExactly("Location");
        assertThat(result.getExcludedHeaders()).containsExactly("Set-Cookie");
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        // when
        String result = new ResponseProperties(true, false, Set.of("Location"), Set.of("Set-Cookie")).toString();

        // then
        assertThat(result).contains(
                "shouldCache4xx=true",
                "shouldCache5xx=false",
                "includedHeaders=[Location]",
                "excludedHeaders=[Set-Cookie]"
        );
    }
}
