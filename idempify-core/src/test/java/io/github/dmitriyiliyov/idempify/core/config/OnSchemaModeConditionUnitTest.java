package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnSchemaModeConditionUnitTest {

    private final OnSchemaModeCondition tested = new OnSchemaModeCondition();
    private final MockEnvironment environment = new MockEnvironment();

    @Mock
    private ConditionContext context;

    @Mock
    private AnnotatedTypeMetadata metadata;

    @Test
    @DisplayName("UT getMatchOutcome() when the property names the annotated mode should match")
    void getMatchOutcome_whenPropertyNamesAnnotatedMode_shouldMatch() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        givenProperty("FLYWAY");

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property names the annotated mode in lower case should match")
    void getMatchOutcome_whenPropertyNamesAnnotatedModeInLowerCase_shouldMatch() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        givenProperty("flyway");

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @Disabled("M10")
    @DisplayName("UT getMatchOutcome() when the property is padded with blanks should match as the binder reads it")
    void getMatchOutcome_whenPropertyIsPaddedWithBlanks_shouldMatchAsBinderReadsIt() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        givenProperty(" FLYWAY ");

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property names another mode should not match")
    void getMatchOutcome_whenPropertyNamesAnotherMode_shouldNotMatch() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        givenProperty("NONE");

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property is absent should leave a migration module out")
    void getMatchOutcome_whenPropertyIsAbsent_shouldLeaveMigrationModuleOut() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        when(context.getEnvironment()).thenReturn(environment);

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isFalse();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property is blank should answer as the built-in default")
    void getMatchOutcome_whenPropertyIsBlank_shouldAnswerAsBuiltInDefault() {
        // given
        givenAnnotatedMode(SchemaMode.NONE);
        givenProperty("  ");

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property names no known mode should refuse rather than not match")
    void getMatchOutcome_whenPropertyNamesNoKnownMode_shouldRefuseRatherThanNotMatch() {
        // given
        givenAnnotatedMode(SchemaMode.FLYWAY);
        givenProperty("liquibase");

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.schema.mode'")
                .hasMessageContaining("liquibase");
    }

    private void givenAnnotatedMode(SchemaMode mode) {
        when(metadata.getAnnotationAttributes(ConditionalOnSchemaMode.class.getName()))
                .thenReturn(Map.of("mode", mode));
    }

    private void givenProperty(String value) {
        environment.setProperty("idempify.schema.mode", value);
        when(context.getEnvironment()).thenReturn(environment);
    }
}
