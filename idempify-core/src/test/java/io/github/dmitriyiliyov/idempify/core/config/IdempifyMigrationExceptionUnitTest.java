package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempifyMigrationExceptionUnitTest {

    @Test
    @DisplayName("UT constructor when cause is null should throw NullPointerException")
    void constructor_whenCauseIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new IdempifyMigrationException("postgresql", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cause cannot be null");
    }

    @Test
    @DisplayName("UT constructor should name the database and keep what the migration failed on")
    void constructor_shouldNameDatabaseAndKeepWhatMigrationFailedOn() {
        // given
        IllegalStateException cause = new IllegalStateException("relation already exists");

        // when
        IdempifyMigrationException tested = new IdempifyMigrationException("postgresql", cause);

        // then
        assertThat(tested.getMessage()).contains("'postgresql'");
        assertThat(tested.getVendor()).isEqualTo("postgresql");
        assertThat(tested.getCause()).isSameAs(cause);
    }
}
