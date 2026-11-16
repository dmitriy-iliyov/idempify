package io.github.dmitriyiliyov.idempify.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor when mode is null should throw NullPointerException")
    void constructor_whenModeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new SchemaProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode cannot be null");
    }
}
