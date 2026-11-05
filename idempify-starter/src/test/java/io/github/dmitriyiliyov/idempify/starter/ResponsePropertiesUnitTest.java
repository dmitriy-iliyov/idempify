package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.Toggle;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What is judged here is that every setting survives the trip, that a block nobody wrote still answers, and that
 * a {@code record-for} nobody wrote stays undecided for the core to answer.
 */
class ResponsePropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when recordFor is not configured should leave it undecided")
    void constructor_whenRecordForIsNotConfigured_shouldLeaveItUndecided() {
        // when
        ResponseProperties result = new ResponseProperties(null, Set.of(), Set.of());

        // then
        assertThat(result.getRecordFor()).isNull();
    }

    @Test
    @DisplayName("UT constructor() when recordFor is not configured should leave the choice open")
    void constructor_whenRecordForIsNotConfigured_shouldLeaveChoiceOpen() {
        // when
        ResponseProperties result = new ResponseProperties(null, Set.of(), Set.of());

        // then
        assertThat(result.getRecordForToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT constructor() when recordFor is configured empty should count as a choice")
    void constructor_whenRecordForIsConfiguredEmpty_shouldCountAsChoice() {
        // when
        ResponseProperties result = new ResponseProperties(List.of(), Set.of(), Set.of());

        // then
        assertThat(result.getRecordForToggle()).isEqualTo(Toggle.ENABLE);
    }

    @Test
    @DisplayName("UT constructor() when recordFor is configured empty should keep no failure")
    void constructor_whenRecordForIsConfiguredEmpty_shouldKeepNoFailure() {
        // when
        ResponseProperties result = new ResponseProperties(List.of(), Set.of(), Set.of());

        // then
        assertThat(result.getRecordFor()).isEmpty();
    }

    @Test
    @DisplayName("UT constructor() when recordFor names a class that is not an exception should throw IllegalArgumentException")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void constructor_whenRecordForNamesNonThrowable_shouldThrowIllegalArgumentException() {
        // given
        List recordFor = List.of(String.class);

        // when / then
        assertThatThrownBy(() -> new ResponseProperties(recordFor, Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.response.record-for'")
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
    @DisplayName("UT toResponseConfig() should carry the exceptions the application keeps on the record")
    void toResponseConfig_shouldCarryExceptionsApplicationKeepsOnRecord() {
        // given
        ResponseProperties tested = new ResponseProperties(
                List.of(IllegalStateException.class), Set.of(), Set.of());

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.getRecordFor()).containsExactly(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT toResponseConfig() when recordFor is not configured should leave it undecided")
    void toResponseConfig_whenRecordForIsNotConfigured_shouldLeaveItUndecided() {
        // given
        ResponseProperties tested = new ResponseProperties(null, Set.of(), Set.of());

        // when
        ResponseConfig result = tested.toResponseConfig();

        // then
        assertThat(result.getRecordFor()).isNull();
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
        assertThat(result.getRecordFor()).containsExactly(IllegalStateException.class);
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
                "recordFor=[class java.lang.IllegalStateException]",
                "includedHeaders=[Location]",
                "excludedHeaders=[Set-Cookie]"
        );
    }
}
