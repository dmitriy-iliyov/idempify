package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsUnitTest {

    @Test
    @DisplayName("UT isBlank() when the string is null should treat it as saying nothing")
    void isBlank_whenStringIsNull_shouldTreatItAsSayingNothing() {
        assertThat(StringUtils.isBlank(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("UT isBlank() when the string holds no visible character should treat it as saying nothing")
    void isBlank_whenStringHoldsNoVisibleCharacter_shouldTreatItAsSayingNothing(String value) {
        assertThat(StringUtils.isBlank(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "Idempotency-Key", "0"})
    @DisplayName("UT isBlank() when the string holds a visible character should treat it as saying something")
    void isBlank_whenStringHoldsVisibleCharacter_shouldTreatItAsSayingSomething(String value) {
        assertThat(StringUtils.isBlank(value)).isFalse();
    }

    @Test
    @DisplayName("UT class should be a utility holder that cannot be instantiated or extended")
    void class_shouldBeUtilityHolderThatCannotBeInstantiatedOrExtended() throws Exception {
        Constructor<StringUtils> constructor = StringUtils.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(StringUtils.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
