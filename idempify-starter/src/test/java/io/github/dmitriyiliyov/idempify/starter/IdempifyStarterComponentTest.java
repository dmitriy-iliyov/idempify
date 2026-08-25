package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.config.FingerprintConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;
import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starter as a whole: the {@code idempify.*} text an application writes, bound by the very binder Spring
 * uses, down to the {@link IdempotencyConfig} the core is handed.
 */
class IdempifyStarterComponentTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyAutoConfiguration.class));

    @Test
    @DisplayName("CT application when it says nothing about idempotency should start on the library's own answers")
    void application_whenItSaysNothingAboutIdempotency_shouldStartOnLibrarysOwnAnswers() {
        contextRunner.run(context -> {
            IdempotencyConfig config = configOf(context);

            assertThat(config.getHeaderName()).isEqualTo("Idempotency-Key");
            assertThat(config.getTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(config.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);

            assertThat(config.getConflictConfig().isEnabled()).isTrue();
            assertThat(config.getConflictConfig().getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);

            assertThat(config.getFingerprintConfig().isEnabled()).isTrue();
            assertThat(config.getFingerprintConfig().getBodyHandleStrategy())
                    .isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
            assertThat(config.getFingerprintConfig().getBodyCanonicalizerConfig())
                    .isEqualTo(BodyCanonicalizerConfig.defaults());

            assertThat(config.getResponseCacheConfig().isEnabled())
                    .describedAs("caching is off until an application asks for it by name")
                    .isFalse();
            assertThat(context.getBean(CachePropertiesHolder.class).getCacheName()).isNull();
        });
    }

    @Test
    @DisplayName("CT application when every block is written should reach the core as one config")
    void application_whenEveryBlockIsWritten_shouldReachCoreAsOneConfig() {
        contextRunner
                .withPropertyValues(
                        "idempify.header-name=X-Request-Id",
                        "idempify.ttl=30m",
                        "idempify.processor-type=LOCK_BASED",
                        "idempify.conflict.enabled=true",
                        "idempify.conflict.strategy=WAIT",
                        "idempify.conflict.wait.delay=250ms",
                        "idempify.conflict.wait.multiplier=2.0",
                        "idempify.conflict.wait.max-attempts=3",
                        "idempify.conflict.wait.max-duration=2m",
                        "idempify.fingerprint.enabled=true",
                        "idempify.fingerprint.strategy=CANONICALIZED_BODY_HASH",
                        "idempify.fingerprint.empty-body-fallback=NOOP",
                        "idempify.fingerprint.canonicalizer.format=JSON",
                        "idempify.fingerprint.canonicalizer.strategy=LEXICOGRAPHICAL",
                        "idempify.fingerprint.canonicalizer.excluded-fields=timestamp,traceId",
                        "idempify.cache.enabled=true",
                        "idempify.cache.cache-name=orders",
                        "idempify.cache.should-cache4xx=true",
                        "idempify.cache.should-cache5xx=true"
                )
                .run(context -> {
                    IdempotencyConfig config = configOf(context);

                    assertThat(config.getHeaderName()).isEqualTo("X-Request-Id");
                    assertThat(config.getTtl()).isEqualTo(Duration.ofMinutes(30));

                    WaitConflictHandlerConfig backoff =
                            (WaitConflictHandlerConfig) config.getConflictConfig().getHandlerConfig();
                    assertThat(config.getConflictConfig().getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
                    assertThat(backoff.getDelay()).isEqualTo(250L);
                    assertThat(backoff.getMultiplier()).isEqualTo(2.0);
                    assertThat(backoff.getMaxAttempts()).isEqualTo(3);
                    assertThat(backoff.getMaxDuration()).isEqualTo(120_000L);

                    BodyCanonicalizerConfig canonicalizer = config.getFingerprintConfig().getBodyCanonicalizerConfig();
                    assertThat(canonicalizer.getFormat()).isEqualTo(BodyFormat.JSON);
                    assertThat(canonicalizer.getCanonicalizeStrategy())
                            .isEqualTo(CanonicalizeStrategy.LEXICOGRAPHICAL);
                    assertThat(canonicalizer.getExcludedFields()).containsExactlyInAnyOrder("timestamp", "traceId");
                    assertThat(config.getFingerprintConfig().getEmptyBodyFallback().fallback(null))
                            .describedAs("NOOP leaves a body-less request to path and method alone")
                            .isEmpty();

                    assertThat(config.getResponseCacheConfig().isEnabled()).isTrue();
                    assertThat(config.getResponseCacheConfig().shouldCache4xx()).isTrue();
                    assertThat(config.getResponseCacheConfig().shouldCache5xx()).isTrue();
                    assertThat(context.getBean(CachePropertiesHolder.class).getCacheName()).isEqualTo("orders");

                    assertThat(config.notEmpty())
                            .describedAs("the global layer answers everything a narrower one may fall back on")
                            .isTrue();
                });
    }

    @Test
    @DisplayName("CT application when the wait block is written only in part should still hand over a whole backoff")
    void application_whenWaitBlockIsWrittenOnlyInPart_shouldStillHandOverWholeBackoff() {
        contextRunner
                .withPropertyValues(
                        "idempify.conflict.strategy=WAIT",
                        "idempify.conflict.wait.max-attempts=9"
                )
                .run(context -> {
                    WaitConflictHandlerConfig backoff =
                            (WaitConflictHandlerConfig) configOf(context).getConflictConfig().getHandlerConfig();

                    assertThat(backoff.notEmpty()).isTrue();
                    assertThat(backoff.getMaxAttempts()).isEqualTo(9);
                    assertThat(backoff.getDelay()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_DELAY_MILLIS);
                    assertThat(backoff.getMultiplier()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MULTIPLIER);
                    assertThat(backoff.getMaxDuration()).isEqualTo(WaitConflictHandlerConfig.DEFAULT_MAX_DURATION_MILLIS);
                });
    }

    @Test
    @DisplayName("CT application when the wait block is written for a strategy that cannot read it should refuse to start")
    void application_whenWaitBlockIsWrittenForStrategyThatCannotReadIt_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues("idempify.conflict.wait.max-attempts=9")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("wait should not be specified when strategy is REJECT");
                });
    }

    @Test
    @DisplayName("CT application when conflict handling is switched off should hand the core a settled answer")
    void application_whenConflictHandlingIsSwitchedOff_shouldHandCoreSettledAnswer() {
        contextRunner
                .withPropertyValues("idempify.conflict.enabled=false")
                .run(context -> {
                    assertThat(configOf(context).getConflictConfig().isEnabled()).isFalse();
                    assertThat(configOf(context).notEmpty()).isTrue();
                });
    }

    @Test
    @DisplayName("CT application when it asks for a byte-level fingerprint should start on that strategy")
    void application_whenItAsksForByteLevelFingerprint_shouldStartOnThatStrategy() {
        contextRunner
                .withPropertyValues("idempify.fingerprint.strategy=RAW_BYTES_HASH")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    FingerprintConfig fingerprint = configOf(context).getFingerprintConfig();
                    assertThat(fingerprint.getBodyHandleStrategy()).isEqualTo(BodyHandleStrategy.RAW_BYTES_HASH);
                    assertThat(fingerprint.getBodyCanonicalizerConfig())
                            .describedAs("the canonicalizer block has nobody to read it under a byte-level strategy")
                            .isNull();
                });
    }

    @Test
    @DisplayName("CT application when it asks for a normalized byte fingerprint should start on that strategy")
    void application_whenItAsksForNormalizedByteFingerprint_shouldStartOnThatStrategy() {
        contextRunner
                .withPropertyValues("idempify.fingerprint.strategy=NORMALIZED_BYTES_HASH")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(configOf(context).getFingerprintConfig().getBodyHandleStrategy())
                            .isEqualTo(BodyHandleStrategy.NORMALIZED_BYTES_HASH);
                });
    }

    @Test
    @DisplayName("CT application when it writes a canonicalizer block under a byte strategy should refuse to start")
    void application_whenItWritesCanonicalizerBlockUnderByteStrategy_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues(
                        "idempify.fingerprint.strategy=RAW_BYTES_HASH",
                        "idempify.fingerprint.canonicalizer.excluded-fields=timestamp"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("canonicalizer should not be specified when strategy is not");
                });
    }

    @Test
    @DisplayName("CT application when fingerprinting is switched off should hand the core a settled answer")
    void application_whenFingerprintingIsSwitchedOff_shouldHandCoreSettledAnswer() {
        contextRunner
                .withPropertyValues("idempify.fingerprint.enabled=false")
                .run(context -> {
                    FingerprintConfig fingerprint = configOf(context).getFingerprintConfig();

                    assertThat(fingerprint.isEnabled()).isFalse();
                    assertThat(fingerprint.getBodyHandleStrategy()).isNull();
                    assertThat(fingerprint.getBodyCanonicalizerConfig()).isNull();
                });
    }

    @Test
    @DisplayName("CT application when the transactional processor is chosen alone should refuse to start naming the conflict property")
    void application_whenTransactionalProcessorIsChosenAlone_shouldRefuseToStartNamingConflictProperty() {
        contextRunner
                .withPropertyValues("idempify.processor-type=TRANSACTIONAL")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("idempify.conflict.enabled must be false");
                });
    }

    @Test
    @DisplayName("CT application when the transactional processor keeps the response cache should refuse to start naming the cache property")
    void application_whenTransactionalProcessorKeepsResponseCache_shouldRefuseToStartNamingCacheProperty() {
        contextRunner
                .withPropertyValues(
                        "idempify.processor-type=TRANSACTIONAL",
                        "idempify.conflict.enabled=false",
                        "idempify.cache.enabled=true",
                        "idempify.cache.cache-name=orders"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("idempify.cache.enabled must be false");
                });
    }

    @Test
    @DisplayName("CT application when the transactional processor switches both sections off should start")
    void application_whenTransactionalProcessorSwitchesBothSectionsOff_shouldStart() {
        contextRunner
                .withPropertyValues(
                        "idempify.processor-type=TRANSACTIONAL",
                        "idempify.conflict.enabled=false"
                )
                .run(context -> {
                    IdempotencyConfig config = configOf(context);

                    assertThat(config.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
                    assertThat(config.getConflictConfig().isEnabled()).isFalse();
                    assertThat(config.getResponseCacheConfig().isEnabled()).isFalse();
                    config.validate();
                });
    }

    @Test
    @DisplayName("CT application when caching is asked for without a name should refuse to start naming both ways out")
    void application_whenCachingIsAskedForWithoutName_shouldRefuseToStartNamingBothWaysOut() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("idempify.cache.cache-name")
                            .hasMessageContaining("idempify.cache.enabled");
                });
    }

    @Test
    @DisplayName("CT application when ttl is not positive should refuse to start")
    void application_whenTtlIsNotPositive_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues("idempify.ttl=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("ttl must be positive");
                });
    }

    @Test
    @DisplayName("CT application when the header name is blank should refuse to start")
    void application_whenHeaderNameIsBlank_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues("idempify.header-name=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("headerName cannot be null, empty or blank");
                });
    }

    @Test
    @DisplayName("CT application when the canonicalizer selects the body both ways should refuse to start")
    void application_whenCanonicalizerSelectsBodyBothWays_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues(
                        "idempify.fingerprint.canonicalizer.included-fields=amount",
                        "idempify.fingerprint.canonicalizer.excluded-fields=timestamp"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("includedFields and excludedFields select the body in opposite ways");
                });
    }

    @Test
    @DisplayName("CT application when a property names an unknown constant should refuse to start")
    void application_whenPropertyNamesUnknownConstant_shouldRefuseToStart() {
        contextRunner
                .withPropertyValues("idempify.processor-type=SOMETHING_ELSE")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("CT application when only some fields take part in the fingerprint should carry them to the core")
    void application_whenOnlySomeFieldsTakePartInFingerprint_shouldCarryThemToCore() {
        contextRunner
                .withPropertyValues("idempify.fingerprint.canonicalizer.included-fields=amount,currency")
                .run(context -> {
                    BodyCanonicalizerConfig canonicalizer =
                            configOf(context).getFingerprintConfig().getBodyCanonicalizerConfig();

                    assertThat(canonicalizer.getIncludedFields()).containsExactlyInAnyOrder("amount", "currency");
                    assertThat(canonicalizer.getExcludedFields()).isEmpty();
                });
    }

    private static IdempotencyConfig configOf(AssertableApplicationContext context) {
        return context.getBean(IdempotencyConfig.class);
    }
}
