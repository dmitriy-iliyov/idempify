package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempifySchemaLocationUnitTest {

    private static final String VENDOR = "postgresql";
    private static final String LOCATION = "classpath:db/idempify/postgresql";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("UT constructor when vendor is null, empty or blank should throw IllegalArgumentException")
    void constructor_whenVendorIsNullEmptyOrBlank_shouldThrowIllegalArgumentException(String vendor) {
        // when / then
        assertThatThrownBy(() -> new IdempifySchemaLocation(vendor, LOCATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vendor cannot be null, empty or blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("UT constructor when location is null, empty or blank should throw IllegalArgumentException")
    void constructor_whenLocationIsNullEmptyOrBlank_shouldThrowIllegalArgumentException(String location) {
        // when / then
        assertThatThrownBy(() -> new IdempifySchemaLocation(VENDOR, location))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("location cannot be null, empty or blank");
    }

    @Test
    @DisplayName("UT constructor when values are padded should keep them stripped")
    void constructor_whenValuesArePadded_shouldKeepThemStripped() {
        // when
        IdempifySchemaLocation tested = new IdempifySchemaLocation("  " + VENDOR + " ", " " + LOCATION + "  ");

        // then
        assertThat(tested.vendor()).isEqualTo(VENDOR);
        assertThat(tested.location()).isEqualTo(LOCATION);
    }
}
