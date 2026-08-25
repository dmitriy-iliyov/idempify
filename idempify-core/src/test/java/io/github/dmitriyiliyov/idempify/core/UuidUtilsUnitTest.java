package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidUtilsUnitTest {

    private static final String CANONICAL = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    @DisplayName("UT isCanonical() when the value is written in the canonical form should be true")
    void isCanonical_whenValueIsWrittenInCanonicalForm_shouldBeTrue() {
        // when / then
        assertThat(UuidUtils.isCanonical(CANONICAL)).isTrue();
    }

    @Test
    @DisplayName("UT isCanonical() when the value is uppercase should be true")
    void isCanonical_whenValueIsUppercase_shouldBeTrue() {
        // when / then
        assertThat(UuidUtils.isCanonical(CANONICAL.toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("UT isCanonical() when the groups are shorter than the canonical ones should be false")
    void isCanonical_whenGroupsAreShorterThanCanonicalOnes_shouldBeFalse() {
        // when / then
        assertThat(UuidUtils.isCanonical("1-2-3-4-5")).isFalse();
    }

    @Test
    @DisplayName("UT isCanonical() when the value is padded with spaces should be false")
    void isCanonical_whenValueIsPaddedWithSpaces_shouldBeFalse() {
        // when / then
        assertThat(UuidUtils.isCanonical(" " + CANONICAL + " ")).isFalse();
    }

    @Test
    @DisplayName("UT isCanonical() when the value carries a non-hex character should be false")
    void isCanonical_whenValueCarriesNonHexCharacter_shouldBeFalse() {
        // when / then
        assertThat(UuidUtils.isCanonical("gaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")).isFalse();
    }

    @Test
    @DisplayName("UT isCanonical() when the value is null, empty or blank should be false")
    void isCanonical_whenValueIsNullEmptyOrBlank_shouldBeFalse() {
        // when / then
        assertThat(UuidUtils.isCanonical(null)).isFalse();
        assertThat(UuidUtils.isCanonical("")).isFalse();
        assertThat(UuidUtils.isCanonical("   ")).isFalse();
    }

    @Test
    @DisplayName("UT parseCanonical() when the value is canonical should return the key it names")
    void parseCanonical_whenValueIsCanonical_shouldReturnKeyItNames() {
        // when
        UUID result = UuidUtils.parseCanonical(CANONICAL);

        // then
        assertThat(result).isEqualTo(UUID.fromString(CANONICAL));
    }

    @Test
    @DisplayName("UT parseCanonical() when the value is uppercase should return the same key as its lowercase form")
    void parseCanonical_whenValueIsUppercase_shouldReturnSameKeyAsLowercaseForm() {
        // when
        UUID result = UuidUtils.parseCanonical(CANONICAL.toUpperCase());

        // then
        assertThat(result).isEqualTo(UUID.fromString(CANONICAL));
    }

    @Test
    @DisplayName("UT parseCanonical() when the value would only be padded into a key should return null")
    void parseCanonical_whenValueWouldOnlyBePaddedIntoKey_shouldReturnNull() {
        // when
        UUID result = UuidUtils.parseCanonical("1-2-3-4-5");

        // then
        assertThat(result).isNull();
        assertThat(UUID.fromString("1-2-3-4-5")).isNotNull();
    }

    @Test
    @DisplayName("UT parseCanonical() when the value is not a uuid at all should return null")
    void parseCanonical_whenValueIsNotUuidAtAll_shouldReturnNull() {
        // when / then
        assertThat(UuidUtils.parseCanonical("not-a-uuid")).isNull();
        assertThat(UuidUtils.parseCanonical(null)).isNull();
    }
}
