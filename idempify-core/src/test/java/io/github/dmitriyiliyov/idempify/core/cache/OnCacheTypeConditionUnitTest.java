package io.github.dmitriyiliyov.idempify.core.cache;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The condition decides which cache backend reaches the context, so a wrong answer here is not a slower
 * application but one with no cache at all - or with two. Every case is therefore about the pair it compares:
 * the type the bean declared, and the type the environment names.
 */
class OnCacheTypeConditionUnitTest {

    private final OnCacheTypeCondition tested = new OnCacheTypeCondition();

    @Test
    @DisplayName("UT getMatchOutcome() when the environment names the declared type should match")
    void getMatchOutcome_whenEnvironmentNamesDeclaredType_shouldMatch() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context("DISTRIBUTED"), metadata(CacheType.DISTRIBUTED));

        // then
        assertThat(outcome.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the environment names another type should not match")
    void getMatchOutcome_whenEnvironmentNamesAnotherType_shouldNotMatch() {
        // when
        ConditionOutcome outcome = tested.getMatchOutcome(
                context("DISTRIBUTED"), metadata(CacheType.IN_MEMORY));

        // then
        assertThat(outcome.isMatch()).isFalse();
        assertThat(outcome.getMessage()).contains("not match");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the environment says nothing should fall back to the built-in default")
    void getMatchOutcome_whenEnvironmentSaysNothing_shouldFallBackToBuiltInDefault() {
        // given
        CacheType builtIn = CacheType.fromStr(IdempifyDefaults.CACHE_TYPE_VALUE);

        // when / then
        assertThat(tested.getMatchOutcome(context(null), metadata(builtIn)).isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the environment names an unknown type should throw instead of leaving the cache out")
    void getMatchOutcome_whenEnvironmentNamesUnknownType_shouldThrowInsteadOfLeavingCacheOut() {
        // given
        ConditionContext context = context("elsewhere");
        AnnotatedTypeMetadata metadata = metadata(CacheType.IN_MEMORY);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elsewhere");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the environment differs in case should still match")
    void getMatchOutcome_whenEnvironmentDiffersInCase_shouldStillMatch() {
        // when / then
        assertThat(tested.getMatchOutcome(context("in_memory"), metadata(CacheType.IN_MEMORY)).isMatch())
                .isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the bean carries no annotation attributes should throw IllegalStateException")
    void getMatchOutcome_whenBeanCarriesNoAnnotationAttributes_shouldThrowIllegalStateException() {
        // given
        ConditionContext context = context("IN_MEMORY");
        AnnotatedTypeMetadata metadata = metadataWithAttributes(null);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is null");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the annotation attributes are empty should throw IllegalStateException")
    void getMatchOutcome_whenAnnotationAttributesAreEmpty_shouldThrowIllegalStateException() {
        // given
        ConditionContext context = context("IN_MEMORY");
        AnnotatedTypeMetadata metadata = metadataWithAttributes(Map.of());

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is empty");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the annotation names no type should throw IllegalStateException")
    void getMatchOutcome_whenAnnotationNamesNoType_shouldThrowIllegalStateException() {
        // given
        ConditionContext context = context("IN_MEMORY");
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("type", null);
        AnnotatedTypeMetadata metadata = metadataWithAttributes(attributes);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'type'");
    }

    private static ConditionContext context(String cacheType) {
        MockEnvironment environment = new MockEnvironment();
        if (cacheType != null) {
            environment.setProperty("idempify.cache.type", cacheType);
        }

        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    private static AnnotatedTypeMetadata metadata(CacheType type) {
        return metadataWithAttributes(Map.of("type", type));
    }

    private static AnnotatedTypeMetadata metadataWithAttributes(Map<String, Object> attributes) {
        AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
        when(metadata.getAnnotationAttributes(ConditionalOnCacheType.class.getName())).thenReturn(attributes);
        return metadata;
    }
}
