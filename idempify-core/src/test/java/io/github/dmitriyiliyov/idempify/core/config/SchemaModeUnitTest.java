package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaModeUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"FLYWAY", "flyway", "Flyway"})
    @DisplayName("UT fromStr() when the name differs only in case should answer the mode")
    void fromStr_whenNameDiffersOnlyInCase_shouldAnswerMode(String name) {
        // when
        SchemaMode result = SchemaMode.fromStr(name);

        // then
        assertThat(result).isEqualTo(SchemaMode.FLYWAY);
    }

    @Test
    @DisplayName("UT fromStr() when the name is the built-in default should leave the schema to the application")
    void fromStr_whenNameIsBuiltInDefault_shouldLeaveSchemaToApplication() {
        // when
        SchemaMode result = SchemaMode.fromStr(IdempifyDefaults.SCHEMA_MODE_VALUE);

        // then
        assertThat(result).isEqualTo(SchemaMode.NONE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"liquibase", "", " "})
    @DisplayName("UT fromStr() when the name is no mode should refuse naming the property and what it was")
    void fromStr_whenNameIsNoMode_shouldRefuseNamingPropertyAndWhatItWas(String name) {
        // when / then
        assertThatThrownBy(() -> SchemaMode.fromStr(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.schema.mode'")
                .hasMessageContaining("but was %s".formatted(name));
    }
}
