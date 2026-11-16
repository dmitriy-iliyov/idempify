package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaMigrator;
import io.github.dmitriyiliyov.idempify.core.config.NoopIdempifySchemaMigrator;
import io.github.dmitriyiliyov.idempify.core.config.SchemaMode;
import io.github.dmitriyiliyov.idempify.starter.IdempifyAutoConfiguration.SchemaMigratorPresenceCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigratorPresenceCheckUnitTest {

    @Test
    @DisplayName("UT constructor when mode is null should throw NullPointerException")
    void constructor_whenModeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new SchemaMigratorPresenceCheck(null, new NoopIdempifySchemaMigrator()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode cannot be null");
    }

    @Test
    @DisplayName("UT constructor when migrator is null should throw NullPointerException")
    void constructor_whenMigratorIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new SchemaMigratorPresenceCheck(SchemaMode.NONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("migrator cannot be null");
    }

    @Test
    @DisplayName("UT constructor when the schema is left to the application should accept the no-op migrator")
    void constructor_whenSchemaIsLeftToApplication_shouldAcceptNoopMigrator() {
        // when / then
        assertThatCode(() -> new SchemaMigratorPresenceCheck(SchemaMode.NONE, new NoopIdempifySchemaMigrator()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = SchemaMode.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("UT constructor when a mode asks for migration and only the no-op is there should refuse naming it")
    void constructor_whenModeAsksForMigrationButOnlyNoopMigratorIsThere_shouldNameModeAndRefuse(SchemaMode mode) {
        // when / then
        assertThatThrownBy(() -> new SchemaMigratorPresenceCheck(mode, new NoopIdempifySchemaMigrator()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempify.schema.mode is %s".formatted(mode))
                .hasMessageContaining("idempify.schema.mode=NONE");
    }

    @ParameterizedTest
    @EnumSource(value = SchemaMode.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("UT constructor when a mode asks for migration and a migrator is registered should accept it")
    void constructor_whenModeAsksForMigrationAndMigratorIsRegistered_shouldAcceptIt(SchemaMode mode) {
        // when / then
        assertThatCode(() -> new SchemaMigratorPresenceCheck(mode, new TestSchemaMigrator()))
                .doesNotThrowAnyException();
    }

    private static final class TestSchemaMigrator implements IdempifySchemaMigrator {

        @Override
        public void migrate() { }
    }
}
