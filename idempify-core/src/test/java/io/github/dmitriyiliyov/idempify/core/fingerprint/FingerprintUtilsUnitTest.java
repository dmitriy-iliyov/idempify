package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.TestRequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The opening of a fingerprint carries the path and the method length-prefixed, so that a value containing
 * the separator cannot be read back as two fields. What that buys is the property tested here: two requests
 * that differ at all must not open the fingerprint the same way.
 */
class FingerprintUtilsUnitTest {

    @Test
    @DisplayName("UT toStringBuilderWithoutBody() should length-prefix both the path and the method")
    void toStringBuilderWithoutBody_shouldLengthPrefixBothPathAndMethod() {
        // when
        String result = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/payments", "POST", "{}")).toString();

        // then
        assertThat(result).isEqualTo("9|/payments|4|POST");
    }

    @Test
    @DisplayName("UT toStringBuilderWithoutBody() when a path carries the separator should still not collide with another request")
    void toStringBuilderWithoutBody_whenPathCarriesSeparator_shouldStillNotCollideWithAnotherRequest() {
        // given
        String one = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/a|4", "POST", "{}")).toString();
        String other = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/a", "4|POST", "{}")).toString();

        // when / then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT toStringBuilderWithoutBody() when only the method differs should open the fingerprint differently")
    void toStringBuilderWithoutBody_whenOnlyMethodDiffers_shouldOpenFingerprintDifferently() {
        // given
        String post = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/payments", "POST", "{}")).toString();
        String put = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/payments", "PUT", "{}")).toString();

        // when / then
        assertThat(post).isNotEqualTo(put);
    }

    @Test
    @DisplayName("UT toStringBuilderWithoutBody() when the same request comes twice should open the fingerprint the same way")
    void toStringBuilderWithoutBody_whenSameRequestComesTwice_shouldOpenFingerprintSameWay() {
        // given
        String first = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/payments", "POST", "{}")).toString();
        String second = FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.of("/payments", "POST", "{\"other\":1}")).toString();

        // when / then
        assertThat(first).isEqualTo(second);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("UT toStringBuilderWithoutBody() when the path says nothing should refuse rather than fingerprint a blank")
    void toStringBuilderWithoutBody_whenPathSaysNothing_shouldRefuseRatherThanFingerprintBlank(String path) {
        assertThatThrownBy(() -> FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.builder().path(path).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("UT toStringBuilderWithoutBody() when the method says nothing should refuse rather than fingerprint a blank")
    void toStringBuilderWithoutBody_whenMethodSaysNothing_shouldRefuseRatherThanFingerprintBlank(String method) {
        assertThatThrownBy(() -> FingerprintUtils.toStringBuilderWithoutBody(
                TestRequestContext.builder().method(method).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }
}
