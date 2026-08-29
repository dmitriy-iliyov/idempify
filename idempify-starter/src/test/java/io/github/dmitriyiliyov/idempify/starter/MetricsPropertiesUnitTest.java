package io.github.dmitriyiliyov.idempify.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new MetricsProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT isEnabled() should answer what was configured")
    void isEnabled_shouldAnswerWhatWasConfigured() {
        // when / then
        assertThat(new MetricsProperties(true).isEnabled()).isTrue();
        assertThat(new MetricsProperties(false).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT toString() should name the switch it carries")
    void toString_shouldNameSwitchItCarries() {
        // when / then
        assertThat(new MetricsProperties(false).toString()).isEqualTo("MetricsProperties{enabled=false}");
    }
}
