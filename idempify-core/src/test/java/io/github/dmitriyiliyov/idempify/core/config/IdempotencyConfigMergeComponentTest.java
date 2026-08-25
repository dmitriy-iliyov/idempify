package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictContext;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolves a call site's named config against the global one through the whole nested tree at once —
 * {@link IdempotencyConfig} over {@link ConflictConfig} over {@link WaitConflictHandlerConfig},
 * {@link FingerprintConfig} over {@link BodyCanonicalizerConfig}, and {@link ResponseCacheConfig}. Nothing is
 * stubbed: these are the value types a consuming application builds itself, and what is under test is how they
 * layer, including the combinations the merge must refuse.
 */
class IdempotencyConfigMergeComponentTest {

    @Test
    @DisplayName("CT resolution when the call site tunes something in every section should layer each over the global one")
    void resolution_whenCallSiteTunesSomethingInEverySection_shouldLayerEachOverGlobalOne() {
        // given
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .headerName("X-Payment-Key")
                .ttl(Duration.ofMinutes(15))
                .conflict(ConflictConfig.wait(WaitConflictHandlerConfig.builder().maxAttempts(9).build()))
                .fingerprint(FingerprintConfig.builder()
                        .bodyCanonicalizerConfig(builder -> builder.includedFields("amount", "currency"))
                        .build())
                .responseCache(ResponseCacheConfig.builder().shouldCache4xx(false).build())
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global(), callSite);

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);

        assertThat(result.getConflictConfig().getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        WaitConflictHandlerConfig backoff = (WaitConflictHandlerConfig) result.getConflictConfig().getHandlerConfig();
        assertThat(backoff.getMaxAttempts()).isEqualTo(9);
        assertThat(backoff.getDelay()).isEqualTo(50L);
        assertThat(backoff.getMaxDuration()).isEqualTo(4_000L);

        assertThat(result.getFingerprintConfig().getBodyHandleStrategy())
                .isEqualTo(BodyHandleStrategy.CANONICALIZED_BODY_HASH);
        BodyCanonicalizerConfig canonicalizer = result.getFingerprintConfig().getBodyCanonicalizerConfig();
        assertThat(canonicalizer.getFormat()).isEqualTo(BodyFormat.XML);
        assertThat(canonicalizer.getIncludedFields()).containsExactlyInAnyOrder("amount", "currency");
        assertThat(canonicalizer.getExcludedFields()).isEmpty();

        assertThat(result.getResponseCacheConfig().isEnabled()).isTrue();
        assertThat(result.getResponseCacheConfig().shouldCache4xx()).isFalse();
        assertThat(result.getResponseCacheConfig().shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("CT resolution when the call site decides nothing should hand back the global config as it stands")
    void resolution_whenCallSiteDecidesNothing_shouldHandBackGlobalConfigAsItStands() {
        // given
        IdempotencyConfig global = global();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global, IdempotencyConfig.builder().build());

        // then
        assertThat(result.getHeaderName()).isEqualTo(global.getHeaderName());
        assertThat(result.getTtl()).isEqualTo(global.getTtl());
        assertThat(result.getProcessorType()).isEqualTo(global.getProcessorType());
        assertThat(result.getConflictConfig()).isEqualTo(global.getConflictConfig());
        assertThat(result.getFingerprintConfig()).isEqualTo(global.getFingerprintConfig());
        assertThat(result.getResponseCacheConfig()).isEqualTo(global.getResponseCacheConfig());
    }

    @Test
    @DisplayName("CT resolution when the call site turns every optional section off should keep only the header and the ttl")
    void resolution_whenCallSiteTurnsEveryOptionalSectionOff_shouldKeepOnlyHeaderAndTtl() {
        // given
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .conflict(ConflictConfig.disabled())
                .fingerprint(FingerprintConfig.disabled())
                .responseCache(ResponseCacheConfig.disabled())
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global(), callSite);

        // then
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(result.getConflictConfig().isEnabled()).isFalse();
        assertThat(result.getFingerprintConfig().isEnabled()).isFalse();
        assertThat(result.getResponseCacheConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("CT resolution when the call site brings a handler and a policy of its own should drop what the global tuned for them")
    void resolution_whenCallSiteBringsHandlerAndPolicyOfItsOwn_shouldDropWhatGlobalTunedForThem() {
        // given
        ConflictHandler handler = new TestConflictHandler();
        FingerprintPolicy policy = new TestFingerprintPolicy();
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .conflict(ConflictConfig.custom(handler))
                .fingerprint(FingerprintConfig.builder().fingerprintPolicy(policy).build())
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global(), callSite);

        // then
        assertThat(result.getConflictConfig().getStrategy()).isNull();
        assertThat(result.getConflictConfig().getHandler()).isSameAs(handler);
        assertThat(result.getConflictConfig().getHandlerConfig()).isNull();

        assertThat(result.getFingerprintConfig().getFingerprintPolicy()).isSameAs(policy);
        assertThat(result.getFingerprintConfig().getBodyHandleStrategy()).isNull();
        assertThat(result.getFingerprintConfig().getBodyCanonicalizerConfig()).isNull();
    }

    @Test
    @DisplayName("CT resolution when resolving twice from the same pair should give the same config both times")
    void resolution_whenResolvingTwiceFromSamePair_shouldGiveSameConfigBothTimes() {
        // given
        IdempotencyConfig global = global();
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .ttl(Duration.ofMinutes(15))
                .responseCache(ResponseCacheConfig.disabled())
                .build();

        // when
        IdempotencyConfig first = IdempotencyConfig.merge(global, callSite);
        IdempotencyConfig second = IdempotencyConfig.merge(global, callSite);

        // then
        assertThat(first.getTtl()).isEqualTo(second.getTtl());
        assertThat(first.getConflictConfig()).isEqualTo(second.getConflictConfig());
        assertThat(first.getFingerprintConfig()).isEqualTo(second.getFingerprintConfig());
        assertThat(first.getResponseCacheConfig()).isEqualTo(second.getResponseCacheConfig());
    }

    @Test
    @DisplayName("CT resolution when the global config leaves a section unspecified should refuse to resolve at all")
    void resolution_whenGlobalConfigLeavesSectionUnspecified_shouldRefuseToResolveAtAll() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.defaults())
                .build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(global, IdempotencyConfig.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reference must specify every setting a merge can fall back on");
    }

    @Test
    @DisplayName("CT resolution when the call site goes transactional while the global handles conflicts should refuse to resolve")
    void resolution_whenCallSiteGoesTransactionalWhileGlobalHandlesConflicts_shouldRefuseToResolve() {
        // given
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .processorType(ProcessorType.TRANSACTIONAL)
                .build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(global(), callSite))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflictConfig cannot be enabled if processorType is TRANSACTIONAL");
    }

    @Test
    @DisplayName("CT resolution when the call site asks a global that caches nothing to cache should leave caching off")
    void resolution_whenCallSiteAsksGlobalThatCachesNothingToCache_shouldLeaveCachingOff() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(ConflictConfig.disabled())
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.disabled())
                .build();
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .responseCache(ResponseCacheConfig.all())
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(global, callSite);

        // then
        assertThat(result.getResponseCacheConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("CT resolution when the call site tunes a canonicalizer the global wrote by hand should refuse to resolve")
    void resolution_whenCallSiteTunesCanonicalizerGlobalWroteByHand_shouldRefuseToResolve() {
        // given
        IdempotencyConfig global = global(FingerprintConfig.builder()
                .bodyCanonicalizer(body -> new String(body, StandardCharsets.UTF_8))
                .build());
        IdempotencyConfig callSite = IdempotencyConfig.builder()
                .fingerprint(FingerprintConfig.builder()
                        .bodyCanonicalizerConfig(builder -> builder.includedFields("amount"))
                        .build())
                .build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(global, callSite))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot take a bodyCanonicalizerConfig");
    }

    /**
     * The least specific source, as an application that uses every feature would hand it over: lock-based, so
     * that conflict handling and response caching are allowed alongside fingerprinting, and tuned away from
     * every built-in default so that a merge keeping a setting cannot be mistaken for one falling back to it.
     */
    private static IdempotencyConfig global() {
        return global(FingerprintConfig.builder()
                .bodyCanonicalizerConfig(builder -> builder
                        .format(BodyFormat.XML)
                        .excludedFields("requestedAt"))
                .emptyBodyFallback(EmptyBodyFallback.NOOP)
                .build());
    }

    private static IdempotencyConfig global(FingerprintConfig fingerprintConfig) {
        return IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.wait(WaitConflictHandlerConfig.builder()
                        .delay(50)
                        .maxAttempts(3)
                        .maxDuration(4_000)
                        .build()))
                .fingerprint(fingerprintConfig)
                .responseCache(ResponseCacheConfig.all())
                .build();
    }

    private static final class TestConflictHandler implements ConflictHandler {

        @Override
        public <T> T handle(ConflictContext<T> context) {
            return null;
        }

        @Override
        public boolean requiresTransaction() {
            return false;
        }
    }

    private static final class TestFingerprintPolicy implements FingerprintPolicy {

        @Override
        public String generate(RequestContext context) {
            return "fingerprint";
        }

        @Override
        public boolean match(String previous, String current) {
            return true;
        }

        @Override
        public void handle(FingerprintMismatchContext context) {
            // nothing to do
        }
    }
}
