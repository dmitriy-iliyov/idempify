package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Opcodes;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The metrics module is not on this module's classpath, so the classes the condition looks for are generated
 * on the fly by a class loader that knows only the names a case hands it.
 */
@ExtendWith(MockitoExtension.class)
class OnMetricsDisabledConditionUnitTest {

    private static final String IDEMPOTENCY_LISTENER = "io.github.dmitriyiliyov.idempify.metrics.MetricsIdempotencyEventListener";
    private static final String CACHE_LISTENER = "io.github.dmitriyiliyov.idempify.metrics.MetricsCacheEventListener";
    private static final String METER_REGISTRY = "io.micrometer.core.instrument.MeterRegistry";

    private final OnMetricsDisabledCondition tested = new OnMetricsDisabledCondition();
    private final MockEnvironment environment = new MockEnvironment();

    @Mock
    private ConditionContext context;

    @Mock
    private AnnotatedTypeMetadata metadata;

    @Test
    @DisplayName("UT getMatchOutcome() when the property is absent should match")
    void getMatchOutcome_whenPropertyIsAbsent_shouldMatch() {
        // given
        givenEnvironment();

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when the property is blank should match")
    void getMatchOutcome_whenPropertyIsBlank_shouldMatch() {
        // given
        environment.setProperty("idempify.metrics.enabled", " ");
        givenEnvironment();

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when metrics are switched off should match without looking at the classpath")
    void getMatchOutcome_whenMetricsAreSwitchedOff_shouldMatchWithoutLookingAtClasspath() {
        // given
        environment.setProperty("idempify.metrics.enabled", "false");
        givenEnvironment();

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isTrue();
    }

    @Test
    @DisplayName("UT getMatchOutcome() when metrics are on and every class is present should not match")
    void getMatchOutcome_whenMetricsAreOnAndEveryClassIsPresent_shouldNotMatch() {
        // given
        givenMetricsEnabledWith(IDEMPOTENCY_LISTENER, CACHE_LISTENER, METER_REGISTRY);

        // when
        ConditionOutcome result = tested.getMatchOutcome(context, metadata);

        // then
        assertThat(result.isMatch()).isFalse();
        assertThat(result.getMessage()).contains("metrics module supplies the listeners");
    }

    @Test
    @DisplayName("UT getMatchOutcome() when metrics are on and the idempotency listener is missing should name it")
    void getMatchOutcome_whenMetricsAreOnAndIdempotencyListenerIsMissing_shouldNameIt() {
        // given
        givenMetricsEnabledWith(CACHE_LISTENER, METER_REGISTRY);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'idempify.metrics.enabled' is true")
                .hasMessageContaining(IDEMPOTENCY_LISTENER);
    }

    @Test
    @DisplayName("UT getMatchOutcome() when metrics are on and only the cache listener is missing should name it")
    void getMatchOutcome_whenMetricsAreOnAndOnlyCacheListenerIsMissing_shouldNameIt() {
        // given
        givenMetricsEnabledWith(IDEMPOTENCY_LISTENER, METER_REGISTRY);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CACHE_LISTENER);
    }

    @Test
    @DisplayName("UT getMatchOutcome() when metrics are on and only micrometer is missing should name its registry")
    void getMatchOutcome_whenMetricsAreOnAndOnlyMicrometerIsMissing_shouldNameItsRegistry() {
        // given
        givenMetricsEnabledWith(IDEMPOTENCY_LISTENER, CACHE_LISTENER);

        // when / then
        assertThatThrownBy(() -> tested.getMatchOutcome(context, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(METER_REGISTRY);
    }

    private void givenEnvironment() {
        when(context.getEnvironment()).thenReturn(environment);
    }

    private void givenMetricsEnabledWith(String... presentClasses) {
        environment.setProperty("idempify.metrics.enabled", "true");
        givenEnvironment();
        lenient().when(context.getClassLoader()).thenReturn(new GeneratingClassLoader(Set.of(presentClasses)));
    }

    /**
     * Knows only the names it was given, and answers each with an empty class generated on first request.
     */
    private static final class GeneratingClassLoader extends ClassLoader {

        private final Set<String> classNames;

        private GeneratingClassLoader(Set<String> classNames) {
            super(OnMetricsDisabledConditionUnitTest.class.getClassLoader());
            this.classNames = classNames;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!classNames.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
            writer.visitEnd();
            byte [] bytes = writer.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
