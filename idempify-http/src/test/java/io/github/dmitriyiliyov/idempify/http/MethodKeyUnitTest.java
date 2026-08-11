package io.github.dmitriyiliyov.idempify.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;

class MethodKeyUnitTest {

    @Test
    @DisplayName("UT equals() when the method and the pattern are the same should be equal")
    void equals_whenMethodAndPatternAreSame_shouldBeEqual() {
        // given
        MethodKey tested = new MethodKey("POST", pattern("/payments"));

        // when / then
        assertThat(tested).isEqualTo(new MethodKey("POST", pattern("/payments")));
        assertThat(tested).hasSameHashCodeAs(new MethodKey("POST", pattern("/payments")));
    }

    @Test
    @DisplayName("UT equals() when the method differs should not be equal")
    void equals_whenMethodDiffers_shouldNotBeEqual() {
        // given
        MethodKey tested = new MethodKey("POST", pattern("/payments"));

        // when / then
        assertThat(tested).isNotEqualTo(new MethodKey("PUT", pattern("/payments")));
    }

    @Test
    @DisplayName("UT equals() when the pattern differs should not be equal")
    void equals_whenPatternDiffers_shouldNotBeEqual() {
        // given
        MethodKey tested = new MethodKey("POST", pattern("/payments"));

        // when / then
        assertThat(tested).isNotEqualTo(new MethodKey("POST", pattern("/orders")));
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        // given
        MethodKey tested = new MethodKey("POST", pattern("/payments"));

        // when / then
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with null or another type should not be equal")
    void equals_whenComparedWithNullOrAnotherType_shouldNotBeEqual() {
        // given
        MethodKey tested = new MethodKey("POST", pattern("/payments"));

        // when / then
        assertThat(tested).isNotEqualTo(null);
        assertThat(tested).isNotEqualTo("POST /payments");
    }

    @Test
    @DisplayName("UT equals() when both fields are null should be equal without throwing")
    void equals_whenBothFieldsAreNull_shouldBeEqualWithoutThrowing() {
        // given
        MethodKey tested = new MethodKey(null, null);

        // when / then
        assertThat(tested).isEqualTo(new MethodKey(null, null));
        assertThat(tested).isNotEqualTo(new MethodKey("POST", pattern("/payments")));
    }

    private static PathPattern pattern(String value) {
        return PathPatternParser.defaultInstance.parse(value);
    }
}
