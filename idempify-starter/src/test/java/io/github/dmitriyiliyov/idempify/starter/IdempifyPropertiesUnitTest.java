package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.cache.CacheType;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import io.github.dmitriyiliyov.idempify.starter.FingerprintProperties.BodyCanonicalizerProperties;
import io.github.dmitriyiliyov.idempify.starter.FingerprintProperties.EmptyBodyFallbackStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempifyPropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().enabled(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the library is switched off should keep no block for a call site that never runs")
    void constructor_whenLibraryIsSwitchedOff_shouldKeepNoBlockForCallSiteThatNeverRuns() {
        // when
        IdempifyProperties tested = properties().enabled(false).build();

        // then
        assertThat(tested.isEnabled()).isFalse();
        assertThat(tested.getHeaderName()).isNull();
        assertThat(tested.getTtl()).isNull();
        assertThat(tested.getProcessorType()).isNull();
        assertThat(tested.getConflict()).isNull();
        assertThat(tested.getFingerprint()).isNull();
        assertThat(tested.getCache()).isNull();
        assertThat(tested.getMetrics()).isNull();
    }

    @Test
    @DisplayName("UT constructor() when the library is switched off should not ask for settings nobody will read")
    void constructor_whenLibraryIsSwitchedOff_shouldNotAskForSettingsNobodyWillRead() {
        // when
        IdempifyProperties tested = properties()
                .enabled(false)
                .headerName("   ")
                .ttl(Duration.ZERO)
                .processorType(null)
                .conflict(null)
                .fingerprint(null)
                .cache(null)
                .metrics(null)
                .build();

        // then
        assertThat(tested.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT constructor() when the library is switched off should not weigh the transactional processor against a section")
    void constructor_whenLibraryIsSwitchedOff_shouldNotWeighTransactionalProcessorAgainstSection() {
        // when
        IdempifyProperties tested = properties()
                .enabled(false)
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(conflictProperties(true))
                .build();

        // then
        assertThat(tested.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT provide() when the library is switched off should refuse naming the property that switched it off")
    void provide_whenLibraryIsSwitchedOff_shouldRefuseNamingPropertyThatSwitchedItOff() {
        // given
        IdempifyProperties tested = properties().enabled(false).build();

        // when / then
        assertThatThrownBy(tested::provide)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'idempify.enabled' is false");
    }

    @Test
    @DisplayName("UT constructor() when headerName is null should throw IllegalArgumentException")
    void constructor_whenHeaderNameIsNull_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> properties().headerName(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("headerName cannot be null, empty or blank");
    }

    @Test
    @DisplayName("UT constructor() when headerName is blank should throw IllegalArgumentException")
    void constructor_whenHeaderNameIsBlank_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> properties().headerName("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("headerName cannot be null, empty or blank");
    }

    @Test
    @DisplayName("UT constructor() when ttl is null should throw NullPointerException")
    void constructor_whenTtlIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().ttl(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ttl cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when ttl is zero should throw IllegalArgumentException")
    void constructor_whenTtlIsZero_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> properties().ttl(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be positive");
    }

    @Test
    @DisplayName("UT constructor() when ttl is negative should throw IllegalArgumentException")
    void constructor_whenTtlIsNegative_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> properties().ttl(Duration.ofSeconds(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be positive");
    }

    @Test
    @DisplayName("UT constructor() when processorType is null should throw NullPointerException")
    void constructor_whenProcessorTypeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().processorType(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("processorType cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the conflict block is null should throw NullPointerException")
    void constructor_whenConflictBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().conflict(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("conflict cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the fingerprint block is null should throw NullPointerException")
    void constructor_whenFingerprintBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().fingerprint(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("fingerprint cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the response block is null should throw NullPointerException")
    void constructor_whenResponseBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().response(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("response cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the cache block is null should throw NullPointerException")
    void constructor_whenCacheBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().cache(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cache cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when the transactional processor meets conflict handling should name the property to switch off")
    void constructor_whenTransactionalProcessorMeetsConflictHandling_shouldNamePropertyToSwitchOff() {
        // when / then
        assertThatThrownBy(() -> properties()
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(conflictProperties(true))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'idempify.conflict.enabled' must be false")
                .hasMessageContaining("'idempify.processor-type' is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT constructor() when the transactional processor keeps client errors should name the property to switch off")
    void constructor_whenTransactionalProcessorKeepsClientErrors_shouldNamePropertyToSwitchOff() {
        // when / then
        assertThatThrownBy(() -> properties()
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(conflictProperties(false))
                .response(new ResponseProperties(true, false, Set.of(), Set.of()))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'idempify.response.should-cache-4xx' and 'idempify.response.should-cache-5xx' must be false")
                .hasMessageContaining("'idempify.processor-type' is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT constructor() when the transactional processor keeps server errors should name the property to switch off")
    void constructor_whenTransactionalProcessorKeepsServerErrors_shouldNamePropertyToSwitchOff() {
        // when / then
        assertThatThrownBy(() -> properties()
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(conflictProperties(false))
                .response(new ResponseProperties(false, true, Set.of(), Set.of()))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'idempify.response.should-cache-4xx' and 'idempify.response.should-cache-5xx' must be false")
                .hasMessageContaining("'idempify.processor-type' is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT constructor() when the transactional processor meets neither section should be accepted")
    void constructor_whenTransactionalProcessorMeetsNeitherSection_shouldBeAccepted() {
        // when
        IdempifyProperties tested = properties()
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(conflictProperties(false))
                .build();

        // then
        assertThat(tested.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
        tested.provide().validate();
    }

    @Test
    @DisplayName("UT provide() should answer every setting the core layers under a call site")
    void provide_shouldAnswerEverySettingCoreLayersUnderCallSite() {
        // given
        IdempifyProperties tested = properties().build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.notEmpty())
                .describedAs("the global layer is the one a merge falls back on, so it may leave nothing open")
                .isTrue();
        result.validate();
    }

    @Test
    @DisplayName("UT provide() should carry the properties it was given")
    void provide_shouldCarryPropertiesItWasGiven() {
        // given
        IdempifyProperties tested = properties()
                .headerName("X-Request-Id")
                .ttl(Duration.ofHours(2))
                .processorType(ProcessorType.LOCK_BASED)
                .build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Request-Id");
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);
    }

    @Test
    @DisplayName("UT provide() when the header name is padded should hand the core the name a request is read by")
    void provide_whenHeaderNameIsPadded_shouldHandCoreNameRequestIsReadBy() {
        // given
        IdempifyProperties tested = properties().headerName("  X-Request-Id  ").build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Request-Id");
    }

    @Test
    @DisplayName("UT getHeaderName() when the name is padded should answer the name a request is read by")
    void getHeaderName_whenNameIsPadded_shouldAnswerNameRequestIsReadBy() {
        // given
        IdempifyProperties tested = properties().headerName("  X-Request-Id  ").build();

        // when / then
        assertThat(tested.getHeaderName()).isEqualTo(tested.provide().getHeaderName());
    }

    @Test
    @DisplayName("UT provide() when conflict handling is switched off should say so instead of leaving it open")
    void provide_whenConflictHandlingIsSwitchedOff_shouldSaySoInsteadOfLeavingItOpen() {
        // given
        IdempifyProperties tested = properties().conflict(conflictProperties(false)).build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getConflictConfig()).isNotNull();
        assertThat(result.getConflictConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT provide() when the conflict block asks to wait should carry the whole backoff")
    void provide_whenConflictBlockAsksToWait_shouldCarryWholeBackoff() {
        // given
        IdempifyProperties tested = properties()
                .conflict(new ConflictProperties(true, ConflictHandleStrategy.WAIT, null))
                .build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getConflictConfig().getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(result.getConflictConfig().getHandlerConfig()).isNotNull();
    }

    @Test
    @DisplayName("UT provide() when fingerprinting is switched off should say so instead of leaving it open")
    void provide_whenFingerprintingIsSwitchedOff_shouldSaySoInsteadOfLeavingItOpen() {
        // given
        IdempifyProperties tested = properties().fingerprint(fingerprintProperties(false)).build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getFingerprintConfig()).isNotNull();
        assertThat(result.getFingerprintConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT provide() when response caching is asked for should carry it")
    void provide_whenResponseCachingIsAskedFor_shouldCarryIt() {
        // given
        IdempifyProperties tested = properties()
                .response(new ResponseProperties(true, false, Set.of(), Set.of()))
                .build();

        // when
        IdempotencyConfig result = tested.provide();

        // then
        assertThat(result.getResponseConfig().shouldCache4xx()).isTrue();
        assertThat(result.getResponseConfig().shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT provide() should produce a config a narrower layer can be merged over")
    void provide_shouldProduceConfigNarrowerLayerCanBeMergedOver() {
        // given
        IdempotencyConfig global = properties().build().provide();
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .headerName("X-Call-Site")
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global, callSite);

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Call-Site");
        assertThat(result.getTtl()).isEqualTo(global.getTtl());
        assertThat(result.getProcessorType()).isEqualTo(global.getProcessorType());
    }

    @Test
    @DisplayName("UT provide() when nothing decides anything should be merged over without changing a setting")
    void provide_whenNothingDecidesAnything_shouldBeMergedOverWithoutChangingSetting() {
        // given
        IdempotencyConfig global = properties().build().provide();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global, IdempotencyConfig.builder().build());

        // then
        assertThat(result.getHeaderName()).isEqualTo(global.getHeaderName());
        assertThat(result.getTtl()).isEqualTo(global.getTtl());
        assertThat(result.getProcessorType()).isEqualTo(global.getProcessorType());
        assertThat(result.getFingerprintConfig()).isEqualTo(global.getFingerprintConfig());
        assertThat(result.getResponseConfig()).isEqualTo(global.getResponseConfig());
    }

    @Test
    @DisplayName("UT constructor() when the metrics block is null should throw NullPointerException")
    void constructor_whenMetricsBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> properties().metrics(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metrics cannot be null");
    }

    @Test
    @DisplayName("UT getters() should answer what was configured")
    void getters_shouldAnswerWhatWasConfigured() {
        // given
        ConflictProperties conflict = conflictProperties(true);
        FingerprintProperties fingerprint = fingerprintProperties(true);
        ResponseProperties response = new ResponseProperties(false, false, Set.of(), Set.of());
        CacheProperties cache = new CacheProperties(false, CacheType.IN_MEMORY, null, 100);
        MetricsProperties metrics = new MetricsProperties(false);

        // when
        IdempifyProperties tested = properties()
                .conflict(conflict)
                .fingerprint(fingerprint)
                .response(response)
                .cache(cache)
                .metrics(metrics)
                .build();

        // then
        assertThat(tested.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(tested.getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(tested.getConflict()).isSameAs(conflict);
        assertThat(tested.getFingerprint()).isSameAs(fingerprint);
        assertThat(tested.getResponse()).isSameAs(response);
        assertThat(tested.getCache()).isSameAs(cache);
        assertThat(tested.getMetrics()).isSameAs(metrics);
    }

    @Test
    @DisplayName("UT toString() should name every block it carries")
    void toString_shouldNameEveryBlockItCarries() {
        // given
        IdempifyProperties tested = properties().build();

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains(
                "enabled=true",
                "headerName='Idempotency-Key'",
                "ttl=PT24H",
                "processorType=LOCK_BASED",
                "conflict=ConflictProperties{",
                "fingerprint=FingerprintProperties{",
                "cache=CacheProperties{",
                "metrics=MetricsProperties{"
        );
    }

    private static PropertiesBuilder properties() {
        return new PropertiesBuilder();
    }

    private static ConflictProperties conflictProperties(boolean enabled) {
        return new ConflictProperties(enabled, ConflictHandleStrategy.REJECT, null);
    }

    private static FingerprintProperties fingerprintProperties(boolean enabled) {
        return new FingerprintProperties(
                enabled,
                BodyHandleStrategy.CANONICALIZED_BODY_HASH,
                EmptyBodyFallbackStrategy.THROWING,
                new BodyCanonicalizerProperties(
                        BodyFormat.JSON, CanonicalizeStrategy.LEXICOGRAPHICAL, Set.of(), Set.of()
                )
        );
    }

    /**
     * Mirrors what the binder hands the constructor when the YAML says nothing, so a test names only the
     * block it is about.
     */
    private static final class PropertiesBuilder {

        private Boolean enabled = true;
        private String headerName = "Idempotency-Key";
        private Duration ttl = Duration.ofHours(24);
        private ProcessorType processorType = ProcessorType.LOCK_BASED;
        private ConflictProperties conflict = conflictProperties(true);
        private FingerprintProperties fingerprint = fingerprintProperties(true);
        private ResponseProperties response = new ResponseProperties(false, false, Set.of(), Set.of());
        private CacheProperties cache = new CacheProperties(false, CacheType.IN_MEMORY, null, 100);
        private MetricsProperties metrics = new MetricsProperties(true);

        private PropertiesBuilder response(ResponseProperties response) {
            this.response = response;
            return this;
        }

        private PropertiesBuilder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        private PropertiesBuilder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        private PropertiesBuilder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        private PropertiesBuilder processorType(ProcessorType processorType) {
            this.processorType = processorType;
            return this;
        }

        private PropertiesBuilder conflict(ConflictProperties conflict) {
            this.conflict = conflict;
            return this;
        }

        private PropertiesBuilder fingerprint(FingerprintProperties fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        private PropertiesBuilder cache(CacheProperties cache) {
            this.cache = cache;
            return this;
        }

        private PropertiesBuilder metrics(MetricsProperties metrics) {
            this.metrics = metrics;
            return this;
        }

        private IdempifyProperties build() {
            return new IdempifyProperties(
                    enabled,
                    headerName,
                    ttl,
                    processorType,
                    conflict,
                    fingerprint,
                    response,
                    cache,
                    metrics
            );
        }
    }

}
