package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.SchemaMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("UT getMode() when a mode is given should answer it")
    void getMode_whenModeIsGiven_shouldAnswerIt() {
        // when
        SchemaProperties tested = new SchemaProperties(SchemaMode.FLYWAY);

        // then
        assertThat(tested.getMode()).isEqualTo(SchemaMode.FLYWAY);
    }
}
