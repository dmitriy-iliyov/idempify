package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The block hands the core a fully decided response policy, so what is judged here is that every setting
 * survives the trip and that a block nobody wrote still answers.
 */
class ResponsePropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when releaseFor is not configured should fall back to the core default list")
    void constructor_whenReleaseForIsNotConfigured_shouldFallBackToCoreDefaultList() {
        // when
        ResponseProperties result = new ResponseProperties(null, Set.of(), Set.of());

        // then
        assertThat(result.getReleaseFor()).isEqualTo(IdempifyDefaults.DEFAULT_RELEASE_FOR_LIST);
    }

    @Test
    @DisplayName("UT constructor() when releaseFor is configured empty should release the key for nothing")
    void constructor_whenReleaseForIsConfiguredEmpty_shouldReleaseKeyForNothing() {
        // when
        ResponseProperties result = new ResponseProperties(List.of(), Set.of(), Set.of());

        // then
        assertThat(result.getReleaseFor()).isEmpty();
    }

    @Test
    @DisplayName("UT constructor() when releaseFor names a class that is not an exception should throw IllegalArgumentException")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void constructor_whenReleaseForNamesNonThrowable_shouldThrowIllegalArgumentException() {
        // given
        List releaseFor = List.of(String.class);

        // when / then
        assertThatThrownBy(() -> new ResponseProperties(releaseFor, Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.response.release-for'")
                .hasMessageContaining(String.class.getName());
    }

    @Test
    @DisplayName("UT constructor() when includedHeaders is null should throw NullPointerException")
    void constructor_whenIncludedHeadersIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(List.of(), null, Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("includedHeaders cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when excludedHeaders is null should throw NullPointerException")
    void constructor_whenExcludedHeadersIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new ResponseProperties(List.of(), Set.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("excludedHeaders cannot be null");
    }

    @Test
    @DisplayName("UT toResponseConfig() should carry the exceptions the application releases the key for")
    void toResponseConfig_shouldCarryExceptionsApplicationReleasesKeyFor() {
        // given
        ResponseProperties tested = new ResponseProperties(
                List.of(IllegalStateException.class), Set.of(), Set.of());

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.getReleaseFor()).containsExactly(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT toResponseConfig() when headers are named should carry both sets lowercased")
    void toResponseConfig_whenHeadersAreNamed_shouldCarryBothSetsLowercased() {
        // given
        ResponseProperties tested = new ResponseProperties(
                List.of(), Set.of("Location", "ETag"), Set.of("Set-Cookie"));

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
        ResponseProperties tested = new ResponseProperties(List.of(), Set.of(), Set.of());

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
                List.of(IllegalStateException.class), Set.of("Location"), Set.of("Set-Cookie"));

        // then
        assertThat(result.getReleaseFor()).containsExactly(IllegalStateException.class);
        assertThat(result.getIncludedHeaders()).containsExactly("Location");
        assertThat(result.getExcludedHeaders()).containsExactly("Set-Cookie");
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        // when
        String result = new ResponseProperties(
                List.of(IllegalStateException.class), Set.of("Location"), Set.of("Set-Cookie")).toString();

        // then
        assertThat(result).contains(
                "releaseFor=[class java.lang.IllegalStateException]",
                "includedHeaders=[Location]",
                "excludedHeaders=[Set-Cookie]"
        );
    }
}
