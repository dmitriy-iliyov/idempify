package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.cache.CacheType;
import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @DefaultValue} in this module names a constant from {@code IdempifyDefaults}, and the config
 * classes of the core derive their typed {@code DEFAULT_*} from the same string, so a default is written once
 * and read from both sides. These tests keep it that way: they read the annotations themselves and check the
 * value each one carries against the typed default the core holds, so a literal written back into an
 * annotation - or a config class going its own way - fails here rather than in production.
 * <p>
 * Some defaults have no typed counterpart in the core to be checked against - the cache block and the metrics
 * switch - so for them the check is only that the annotation still reads the shared constant instead of a
 * literal of its own.
 */
class PropertyDefaultsUnitTest {

    @Test
    @DisplayName("UT constructor defaults of the idempify block should repeat the answers core holds")
    void constructorDefaults_ofIdempifyBlock_shouldRepeatAnswersCoreHolds() {
        // when
        String headerName = defaultValueOf(IdempifyProperties.class, "headerName");
        String ttl = defaultValueOf(IdempifyProperties.class, "ttl");
        String processorType = defaultValueOf(IdempifyProperties.class, "processorType");

        // then
        assertThat(headerName).isEqualTo(IdempifyDefaults.HEADER_NAME);
        assertThat(DurationStyle.detectAndParse(ttl)).isEqualTo(Duration.parse(IdempifyDefaults.TTL_VALUE));
        assertThat(ProcessorType.valueOf(processorType))
                .isEqualTo(ProcessorType.valueOf(IdempifyDefaults.PROCESSOR_TYPE_NAME));
    }

    @Test
    @DisplayName("UT constructor defaults of the conflict block should repeat the answers core holds")
    void constructorDefaults_ofConflictBlock_shouldRepeatAnswersCoreHolds() {
        // given
        ConflictConfig defaults = ConflictConfig.defaults();

        // when
        String enabled = defaultValueOf(ConflictProperties.class, "enabled");
        String strategy = defaultValueOf(ConflictProperties.class, "strategy");

        // then
        assertThat(Boolean.valueOf(enabled)).isEqualTo(defaults.isEnabled());
        assertThat(ConflictHandleStrategy.valueOf(strategy)).isEqualTo(defaults.getStrategy());
    }

    @Test
    @DisplayName("UT constructor defaults of the wait block should repeat the backoff core holds")
    void constructorDefaults_ofWaitBlock_shouldRepeatBackoffCoreHolds() {
        // when
        String delay = defaultValueOf(ConflictProperties.WaitConflictProperties.class, "delay");
        String multiplier = defaultValueOf(ConflictProperties.WaitConflictProperties.class, "multiplier");
        String maxAttempts = defaultValueOf(ConflictProperties.WaitConflictProperties.class, "maxAttempts");
        String maxDuration = defaultValueOf(ConflictProperties.WaitConflictProperties.class, "maxDuration");

        // then
        assertThat(DurationStyle.detectAndParse(delay).toMillis())
                .isEqualTo(WaitConflictHandlerConfig.DEFAULT_DELAY_MILLIS);
        assertThat(Double.valueOf(multiplier)).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MULTIPLIER);
        assertThat(Integer.valueOf(maxAttempts)).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_ATTEMPTS);
        assertThat(DurationStyle.detectAndParse(maxDuration).toMillis())
                .isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_DURATION_MILLIS);
    }

    @Test
    @DisplayName("UT constructor defaults of the fingerprint block should repeat the answers core holds")
    void constructorDefaults_ofFingerprintBlock_shouldRepeatAnswersCoreHolds() {
        // when
        String enabled = defaultValueOf(FingerprintProperties.class, "enabled");
        String strategy = defaultValueOf(FingerprintProperties.class, "strategy");
        String emptyBodyFallback = defaultValueOf(FingerprintProperties.class, "emptyBodyFallback");

        // then
        assertThat(Boolean.valueOf(enabled)).isEqualTo(FingerprintConfig.defaults().isEnabled());
        assertThat(BodyHandleStrategy.valueOf(strategy)).isEqualTo(FingerprintConfig.DEFAULT_BODY_HANDLE_STRATEGY);
        assertThat(fingerprintPropertiesFallingBackTo(emptyBodyFallback))
                .isInstanceOf(FingerprintConfig.DEFAULT_EMPTY_BODY_FALLBACK.getClass());
    }

    @Test
    @DisplayName("UT constructor defaults of the canonicalizer block should repeat the answers core holds")
    void constructorDefaults_ofCanonicalizerBlock_shouldRepeatAnswersCoreHolds() {
        // when
        String format = defaultValueOf(FingerprintProperties.BodyCanonicalizerProperties.class, "format");
        String strategy = defaultValueOf(FingerprintProperties.BodyCanonicalizerProperties.class, "strategy");

        // then
        assertThat(BodyFormat.valueOf(format)).isEqualTo(BodyCanonicalizerConfig.DEFAULT_FORMAT);
        assertThat(CanonicalizeStrategy.valueOf(strategy))
                .isEqualTo(BodyCanonicalizerConfig.DEFAULT_CANONICALIZE_STRATEGY);
    }

    @Test
    @DisplayName("UT constructor defaults of the canonicalizer block when no field is named should select the body the way core does")
    void constructorDefaults_ofCanonicalizerBlockWhenNoFieldIsNamed_shouldSelectBodyWayCoreDoes() {
        // given
        FingerprintProperties.BodyCanonicalizerProperties properties =
                new FingerprintProperties.BodyCanonicalizerProperties(
                        BodyCanonicalizerConfig.DEFAULT_FORMAT,
                        BodyCanonicalizerConfig.DEFAULT_CANONICALIZE_STRATEGY,
                        BodyCanonicalizerConfig.DEFAULT_INCLUDED_FIELDS,
                        BodyCanonicalizerConfig.DEFAULT_EXCLUDED_FIELDS
                );

        // when / then
        assertThat(properties.toBodyCanonicalizerConfig()).isEqualTo(BodyCanonicalizerConfig.defaults());
    }

    @Test
    @DisplayName("UT constructor defaults of the response block should repeat the answers core holds")
    void constructorDefaults_ofResponseBlock_shouldRepeatAnswersCoreHolds() {
        // when
        String shouldCache4xx = defaultValueOf(ResponseProperties.class, "shouldCache4xx");
        String shouldCache5xx = defaultValueOf(ResponseProperties.class, "shouldCache5xx");

        // then
        assertThat(Boolean.parseBoolean(shouldCache4xx)).isEqualTo(ResponseConfig.DEFAULT_SHOULD_CACHE_4XX);
        assertThat(Boolean.parseBoolean(shouldCache5xx)).isEqualTo(ResponseConfig.DEFAULT_SHOULD_CACHE_5XX);
    }

    @Test
    @DisplayName("UT constructor defaults of the cache block should read the constants rather than literals")
    void constructorDefaults_ofCacheBlock_shouldReadConstantsRatherThanLiterals() {
        // when
        String enabled = defaultValueOf(CacheProperties.class, "enabled");
        String type = defaultValueOf(CacheProperties.class, "type");
        String capacity = defaultValueOf(CacheProperties.class, "capacity");

        // then
        assertThat(enabled).isEqualTo(IdempifyDefaults.CACHE_ENABLED_VALUE);
        assertThat(type).isEqualTo(IdempifyDefaults.CACHE_TYPE_VALUE);
        assertThat(capacity).isEqualTo(IdempifyDefaults.IN_MEMORY_CACHE_CAPACITY_VALUE);

        assertThat(Boolean.valueOf(enabled))
                .describedAs("no cache stands in front of the store until an application asks for one")
                .isFalse();

        CacheProperties asked = new CacheProperties(
                true,
                CacheType.fromStr(type),
                null,
                Integer.valueOf(capacity)
        );
        assertThat(asked.getType()).isEqualTo(CacheType.IN_MEMORY);
        assertThat(asked.getCacheCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("UT constructor defaults of the metrics block should read the constant rather than a literal")
    void constructorDefaults_ofMetricsBlock_shouldReadConstantRatherThanLiteral() {
        // when
        String enabled = defaultValueOf(MetricsProperties.class, "enabled");

        // then
        assertThat(enabled).isEqualTo(IdempifyDefaults.METRICS_ENABLED_VALUE);
        assertThat(new MetricsProperties(Boolean.valueOf(enabled)).isEnabled()).isFalse();
    }

    private Object fingerprintPropertiesFallingBackTo(String emptyBodyFallback) {
        FingerprintProperties properties = new FingerprintProperties(
                true,
                FingerprintConfig.DEFAULT_BODY_HANDLE_STRATEGY,
                FingerprintProperties.EmptyBodyFallbackStrategy.valueOf(emptyBodyFallback),
                null
        );
        return properties.toFingerprintConfig().getEmptyBodyFallback();
    }

    private String defaultValueOf(Class<?> propertiesType, String parameterName) {
        Constructor<?> constructor = propertiesType.getDeclaredConstructors()[0];
        for (Parameter parameter : constructor.getParameters()) {
            if (parameter.getName().equals(parameterName)) {
                DefaultValue defaultValue = parameter.getAnnotation(DefaultValue.class);
                if (defaultValue == null || defaultValue.value().length != 1) {
                    throw new IllegalStateException(
                            "%s#%s carries no single @DefaultValue".formatted(propertiesType.getName(), parameterName)
                    );
                }
                return defaultValue.value()[0];
            }
        }
        throw new IllegalArgumentException(
                "%s has no constructor parameter named %s".formatted(propertiesType.getName(), parameterName)
        );
    }
}
