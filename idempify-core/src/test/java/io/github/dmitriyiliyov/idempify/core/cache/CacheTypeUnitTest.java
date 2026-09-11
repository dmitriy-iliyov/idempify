package io.github.dmitriyiliyov.idempify.core.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The property naming the cache backend is read as text, so what is judged here is the only thing that turns
 * that text into a decision - including the refusal, which is what keeps an unknown value from quietly
 * leaving the application with no cache.
 */
class CacheTypeUnitTest {

    @ParameterizedTest
    @EnumSource(CacheType.class)
    @DisplayName("UT fromStr() when the value names a type should return it")
    void fromStr_whenValueNamesType_shouldReturnIt(CacheType type) {
        // when / then
        assertThat(CacheType.fromStr(type.name())).isEqualTo(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"in_memory", "In_Memory", "IN_MEMORY"})
    @DisplayName("UT fromStr() when the value differs in case should still name the type")
    void fromStr_whenValueDiffersInCase_shouldStillNameType(String value) {
        // when / then
        assertThat(CacheType.fromStr(value)).isEqualTo(CacheType.IN_MEMORY);
    }

    @Test
    @DisplayName("UT fromStr() when the value names no type should name the property, the value and what was allowed")
    void fromStr_whenValueNamesNoType_shouldNamePropertyValueAndWhatWasAllowed() {
        // when / then
        assertThatThrownBy(() -> CacheType.fromStr("elsewhere"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempify.cache.type")
                .hasMessageContaining("elsewhere")
                .hasMessageContaining("IN_MEMORY")
                .hasMessageContaining("DISTRIBUTED")
                .hasMessageContaining("CUSTOM");
    }

    @Test
    @DisplayName("UT fromStr() when the value is null should throw IllegalArgumentException")
    void fromStr_whenValueIsNull_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> CacheType.fromStr(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UT fromStr() when the value is blank should throw IllegalArgumentException")
    void fromStr_whenValueIsBlank_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> CacheType.fromStr("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
