package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.*;
import io.github.dmitriyiliyov.idempify.core.conflict.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests are written from the contract of {@link OperationMetadataManager} - "layering the sources from the
 * most specific to the least, a setting left unspecified by one layer is decided by the next" - and from the
 * one {@link OperationMetadata} states: by the time a processor sees it, nothing is left to decide.
 */
class DefaultOperationMetadataManagerUnitTest {

    private static final String CONFIG_NAME = "payments";
    private static final long TUNED_DELAY_MILLIS = 1_000L;

    @Test
    @DisplayName("UT constructor when defaultConfig is null should throw NullPointerException")
    void constructor_whenDefaultConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataManager(
                null, new DefaultIdempotencyConfigRegistry(), conflictHandlerProvider(), fingerprintPolicyProvider()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("defaultConfig cannot be null");
    }

    @Test
    @DisplayName("UT constructor when configRegistry is null should throw NullPointerException")
    void constructor_whenConfigRegistryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataManager(
                global(), null, conflictHandlerProvider(), fingerprintPolicyProvider()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configRegistry cannot be null");
    }

    @Test
    @DisplayName("UT constructor when conflictHandlerProvider is null should throw NullPointerException")
    void constructor_whenConflictHandlerProviderIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), null, fingerprintPolicyProvider()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictHandlerProvider cannot be null");
    }

    @Test
    @DisplayName("UT constructor when fingerprintPolicyProvider is null should throw NullPointerException")
    void constructor_whenFingerprintPolicyProviderIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), conflictHandlerProvider(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintPolicyProvider cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the call site specifies nothing should answer every setting from the global config")
    void merge_whenCallSiteSpecifiesNothing_shouldAnswerEverySettingFromGlobalConfig() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().build());

        // then
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);
    }

    @Test
    @DisplayName("UT merge() should leave nothing undecided for the processor to work out")
    void merge_shouldLeaveNothingUndecidedForProcessorToWorkOut() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().build());

        // then
        assertThat(result.getHeaderName()).isNotNull();
        assertThat(result.getTtl()).isNotNull();
        assertThat(result.getProcessorType()).isNotNull();
        assertThat(result.getResponseCacheConfig()).isNotNull();
    }

    @Test
    @DisplayName("UT merge() when the call site names a header should let it win over the global one")
    void merge_whenCallSiteNamesHeader_shouldLetItWinOverGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().headerName("X-Payment-Key").build());

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
    }

    @Test
    @DisplayName("UT merge() when the call site takes its key from an expression should resolve metadata that reads no header")
    void merge_whenCallSiteTakesKeyFromExpression_shouldResolveMetadataThatReadsNoHeader() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().useHeaderName(false).build());

        // then
        assertThat(result.getHeaderName())
                .describedAs("the global header name must not fill in for a key that comes from an expression")
                .isNull();
        assertThat(result.useHeaderName()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the call site says nothing about the key source should keep reading the global header")
    void merge_whenCallSiteSaysNothingAboutKeySource_shouldKeepReadingGlobalHeader() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().build());

        // then
        assertThat(result.useHeaderName()).isTrue();
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
    }

    @Test
    @DisplayName("UT merge() when the call site names a ttl should let it win over the global one")
    void merge_whenCallSiteNamesTtl_shouldLetItWinOverGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().ttl(30).timeUnit(java.util.concurrent.TimeUnit.MINUTES).build());

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("UT merge() when the call site asks for a zero ttl should honour it rather than fall back to the global one")
    void merge_whenCallSiteAsksForZeroTtl_shouldHonourItRatherThanFallBackToGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().ttl(0).build());

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("UT merge() when the call site picks a processor should let it win over the global one")
    void merge_whenCallSitePicksProcessor_shouldLetItWinOverGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().processorType(ProcessorTypeToggle.TRANSACTIONAL).build());

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
    }

    @Test
    @DisplayName("UT merge() when the call site leaves the processor unselected should keep the global choice")
    void merge_whenCallSiteLeavesProcessorUnselected_shouldKeepGlobalChoice() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(ConflictConfig.disabled())
                .fingerprint(FingerprintConfig.builder().bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH).build())
                .responseCache(ResponseCacheConfig.disabled())
                .build();
        DefaultOperationMetadataManager tested = manager(global);

        // when
        OperationMetadata result = tested.merge(raw().processorType(ProcessorTypeToggle.UNSELECTED).build());

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
    }

    @Test
    @DisplayName("UT merge() when the call site turns fingerprinting on over a global that already has it should keep it on")
    void merge_whenCallSiteTurnsFingerprintingOnOverGlobalThatAlreadyHasIt_shouldKeepItOn() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().fingerprintToggle(Toggle.ENABLE).build());

        // then
        assertThat(result.useFingerprint()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the call site turns fingerprinting on over a global that has none should switch it on")
    void merge_whenCallSiteTurnsFingerprintingOnOverGlobalThatHasNone_shouldSwitchItOn() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder(global())
                .fingerprint(FingerprintConfig.disabled())
                .build();
        DefaultOperationMetadataManager tested = manager(global);

        // when
        OperationMetadata result = tested.merge(raw().fingerprintToggle(Toggle.ENABLE).build());

        // then
        assertThat(result.useFingerprint()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the call site turns fingerprinting off should switch it off")
    void merge_whenCallSiteTurnsFingerprintingOff_shouldSwitchItOff() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().fingerprintToggle(Toggle.DISABLE).build());

        // then
        assertThat(result.useFingerprint()).isFalse();
        assertThat(result.getFingerprintPolicy()).isNull();
    }

    @Test
    @DisplayName("UT merge() when the call site leaves fingerprinting unselected should keep the global answer")
    void merge_whenCallSiteLeavesFingerprintingUnselected_shouldKeepGlobalAnswer() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().fingerprintToggle(Toggle.UNSELECTED).build());

        // then
        assertThat(result.useFingerprint()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the call site turns caching off should switch it off")
    void merge_whenCallSiteTurnsCachingOff_shouldSwitchItOff() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().cacheToggle(Toggle.DISABLE).build());

        // then
        assertThat(result.getResponseCacheConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the call site tunes only 4xx caching should keep the global answer for 5xx")
    void merge_whenCallSiteTunesOnly4xxCaching_shouldKeepGlobalAnswerFor5xx() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder(global())
                .responseCache(ResponseCacheConfig.builder().enabled(true).shouldCache4xx(true).shouldCache5xx(true).build())
                .build();
        DefaultOperationMetadataManager tested = manager(global);

        // when
        OperationMetadata result = tested.merge(raw().cacheToggle(Toggle.ENABLE).cache4xxToggle(Toggle.DISABLE).build());

        // then
        assertThat(result.getResponseCacheConfig().shouldCache4xx()).isFalse();
        assertThat(result.getResponseCacheConfig().shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the call site picks a conflict strategy should let it win over the global one")
    void merge_whenCallSitePicksConflictStrategy_shouldLetItWinOverGlobalOne() {
        // given
        RecordingConflictHandlerProvider provider = new RecordingConflictHandlerProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), provider, fingerprintPolicyProvider());

        // when
        tested.merge(raw().conflictHandleStrategy(ConflictHandleStrategyToggle.REJECT).build());

        // then
        assertThat(provider.lastConfig.getStrategy()).isEqualTo(ConflictHandleStrategy.REJECT);
    }

    @Test
    @DisplayName("UT merge(name) should layer the call site over the named config and the named config over the global one")
    void mergeWithName_shouldLayerCallSiteOverNamedConfigAndNamedConfigOverGlobalOne() {
        // given
        IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
        registry.register(CONFIG_NAME, IdempotencyConfig.builder()
                .headerName("X-Named-Key")
                .ttl(Duration.ofHours(1))
                .build());
        DefaultOperationMetadataManager tested = manager(global(), registry);

        // when
        OperationMetadata result = tested.merge(raw().ttl(15).timeUnit(java.util.concurrent.TimeUnit.MINUTES).build(), CONFIG_NAME);

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(result.getHeaderName()).isEqualTo("X-Named-Key");
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);
    }

    @Test
    @DisplayName("UT merge(name) when no config is registered under that name should throw IllegalStateException naming it")
    void mergeWithName_whenNoConfigIsRegisteredUnderThatName_shouldThrowIllegalStateExceptionNamingIt() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when / then
        assertThatThrownBy(() -> tested.merge(raw().build(), "missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("UT merge() when the same call site is resolved twice should give equal answers")
    void merge_whenSameCallSiteIsResolvedTwice_shouldGiveEqualAnswers() {
        // given
        DefaultOperationMetadataManager tested = manager(global());
        RawOperationMetadata rawMetadata = raw().headerName("X-Payment-Key").ttl(5).build();

        // when
        OperationMetadata first = tested.merge(rawMetadata);
        OperationMetadata second = tested.merge(rawMetadata);

        // then
        assertThat(first.getHeaderName()).isEqualTo(second.getHeaderName());
        assertThat(first.getTtl()).isEqualTo(second.getTtl());
        assertThat(first.getProcessorType()).isEqualTo(second.getProcessorType());
    }

    @Test
    @DisplayName("UT constructor when the global config leaves a section out should throw IllegalArgumentException")
    void constructor_whenGlobalConfigLeavesSectionOut_shouldThrowIllegalArgumentException() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .build();

        // when / then
        assertThatThrownBy(() -> manager(global))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultConfig must specify every setting a merge can fall back on");
    }

    @Test
    @DisplayName("UT merge(name) when the raw metadata is null should throw NullPointerException")
    void mergeWithName_whenRawMetadataIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> manager(global()).merge(null, CONFIG_NAME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadata cannot be null");
    }

    @Test
    @DisplayName("UT merge(name) when the config name says nothing should refuse rather than look it up")
    void mergeWithName_whenConfigNameSaysNothing_shouldRefuseRatherThanLookItUp() {
        assertThatThrownBy(() -> manager(global()).merge(raw().build(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configName");
    }

    @Test
    @DisplayName("UT merge() when the call site tunes only 5xx caching should keep the global answer for 4xx")
    void merge_whenCallSiteTunesOnly5xxCaching_shouldKeepGlobalAnswerFor4xx() {
        // given
        IdempotencyConfig global = IdempotencyConfig.builder(global())
                .responseCache(ResponseCacheConfig.builder().enabled(true).shouldCache4xx(true).shouldCache5xx(false).build())
                .build();
        DefaultOperationMetadataManager tested = manager(global);

        // when
        OperationMetadata result = tested.merge(raw().cacheToggle(Toggle.ENABLE).cache5xxToggle(Toggle.ENABLE).build());

        // then
        assertThat(result.getResponseCacheConfig().shouldCache5xx()).isTrue();
        assertThat(result.getResponseCacheConfig().shouldCache4xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the raw metadata is null should throw NullPointerException")
    void merge_whenRawMetadataIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> manager(global()).merge(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadata cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the call site goes transactional should leave it neither a conflict handler nor caching")
    void merge_whenCallSiteGoesTransactional_shouldLeaveItNeitherConflictHandlerNorCaching() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().processorType(ProcessorTypeToggle.TRANSACTIONAL).build());

        // then
        assertThat(result.getConflictHandler()).isNull();
        assertThat(result.getResponseCacheConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the call site repeats the global conflict strategy should keep the backoff tuned for it")
    void merge_whenCallSiteRepeatsGlobalConflictStrategy_shouldKeepBackoffTunedForIt() {
        // given
        WaitConflictHandlerConfig tunedBackoff = WaitConflictHandlerConfig.builder(WaitConflictHandlerConfig.defaults())
                .delay(TUNED_DELAY_MILLIS)
                .build();
        RecordingConflictHandlerProvider provider = new RecordingConflictHandlerProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                IdempotencyConfig.builder(global()).conflict(ConflictConfig.wait(tunedBackoff)).build(),
                new DefaultIdempotencyConfigRegistry(),
                provider,
                fingerprintPolicyProvider());

        // when
        tested.merge(raw().conflictHandleStrategy(ConflictHandleStrategyToggle.WAIT).build());

        // then
        assertThat(provider.lastConfig.getHandlerConfig()).isEqualTo(tunedBackoff);
    }

    @Test
    @DisplayName("UT merge() when the call site picks another conflict strategy should drop the backoff tuned for the global one")
    void merge_whenCallSitePicksAnotherConflictStrategy_shouldDropBackoffTunedForGlobalOne() {
        // given
        RecordingConflictHandlerProvider provider = new RecordingConflictHandlerProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), provider, fingerprintPolicyProvider());

        // when
        tested.merge(raw().conflictHandleStrategy(ConflictHandleStrategyToggle.REJECT).build());

        // then
        assertThat(provider.lastConfig.getHandlerConfig())
                .isEqualTo(ConflictConfig.getDefaultHandlerConfig(ConflictHandleStrategy.REJECT));
    }

    @Test
    @DisplayName("UT merge() when the global handles no conflict and the call site waits should wait with the default backoff")
    void merge_whenGlobalHandlesNoConflictAndCallSiteWaits_shouldWaitWithDefaultBackoff() {
        // given
        RecordingConflictHandlerProvider provider = new RecordingConflictHandlerProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                IdempotencyConfig.builder(global()).conflict(ConflictConfig.disabled()).build(),
                new DefaultIdempotencyConfigRegistry(),
                provider,
                fingerprintPolicyProvider());

        // when
        tested.merge(raw().conflictHandleStrategy(ConflictHandleStrategyToggle.WAIT).build());

        // then
        assertThat(provider.lastConfig.isEnabled()).isTrue();
        assertThat(provider.lastConfig.getStrategy()).isEqualTo(ConflictHandleStrategy.WAIT);
        assertThat(provider.lastConfig.getHandlerConfig()).isEqualTo(WaitConflictHandlerConfig.defaults());
    }

    @Test
    @DisplayName("UT merge() when the call site leaves the conflict strategy unselected should keep the global answer")
    void merge_whenCallSiteLeavesConflictStrategyUnselected_shouldKeepGlobalAnswer() {
        // given
        RecordingConflictHandlerProvider provider = new RecordingConflictHandlerProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), provider, fingerprintPolicyProvider());

        // when
        tested.merge(raw().conflictHandleStrategy(ConflictHandleStrategyToggle.UNSELECTED).build());

        // then
        assertThat(provider.lastConfig).isEqualTo(global().getConflictConfig());
    }

    @Test
    @DisplayName("UT merge() when the call site names a blank header should keep the global one")
    void merge_whenCallSiteNamesBlankHeader_shouldKeepGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(new UnspecifiedRawOperationMetadata("   ", Duration.ofMinutes(5)));

        // then
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
    }

    @Test
    @DisplayName("UT merge() when the call site asks for a negative ttl should keep the global one")
    void merge_whenCallSiteAsksForNegativeTtl_shouldKeepGlobalOne() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(new UnspecifiedRawOperationMetadata("X-Payment-Key", Duration.ofMinutes(-5)));

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("UT merge() when the call site turns fingerprinting off should hand a disabled config to the policy provider")
    void merge_whenCallSiteTurnsFingerprintingOff_shouldHandDisabledConfigToPolicyProvider() {
        // given
        RecordingFingerprintPolicyProvider provider = new RecordingFingerprintPolicyProvider();
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                global(), new DefaultIdempotencyConfigRegistry(), conflictHandlerProvider(), provider);

        // when
        tested.merge(raw().fingerprintToggle(Toggle.DISABLE).build());

        // then
        assertThat(provider.lastConfig.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the call site decides nothing about caching should hand the global answer over untouched")
    void merge_whenCallSiteDecidesNothingAboutCaching_shouldHandGlobalAnswerOverUntouched() {
        // given
        DefaultOperationMetadataManager tested = manager(global());

        // when
        OperationMetadata result = tested.merge(raw().build());

        // then
        assertThat(result.getResponseCacheConfig()).isEqualTo(ResponseCacheConfig.all());
    }

    @Test
    @DisplayName("UT merge(name) when the named config turns fingerprinting off and the call site turns it on should take the global settings")
    void mergeWithName_whenNamedConfigTurnsFingerprintingOffAndCallSiteTurnsItOn_shouldTakeGlobalSettings() {
        // given
        RecordingFingerprintPolicyProvider provider = new RecordingFingerprintPolicyProvider();
        IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
        registry.register(CONFIG_NAME, IdempotencyConfig.builder()
                .fingerprint(FingerprintConfig.disabled())
                .build());
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                global(), registry, conflictHandlerProvider(), provider);

        // when
        tested.merge(raw().fingerprintToggle(Toggle.ENABLE).build(), CONFIG_NAME);

        // then
        assertThat(provider.lastConfig).isEqualTo(global().getFingerprintConfig());
    }

    @Test
    @DisplayName("UT merge(name) when no layer fingerprints and the call site turns it on should fall back to the library defaults")
    void mergeWithName_whenNoLayerFingerprintsAndCallSiteTurnsItOn_shouldFallBackToLibraryDefaults() {
        // given
        RecordingFingerprintPolicyProvider provider = new RecordingFingerprintPolicyProvider();
        IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
        registry.register(CONFIG_NAME, IdempotencyConfig.builder()
                .fingerprint(FingerprintConfig.disabled())
                .build());
        DefaultOperationMetadataManager tested = new DefaultOperationMetadataManager(
                IdempotencyConfig.builder(global()).fingerprint(FingerprintConfig.disabled()).build(),
                registry,
                conflictHandlerProvider(),
                provider);

        // when
        tested.merge(raw().fingerprintToggle(Toggle.ENABLE).build(), CONFIG_NAME);

        // then
        assertThat(provider.lastConfig).isEqualTo(FingerprintConfig.defaults());
    }

    @Test
    @DisplayName("UT merge(name) when the named config picks a processor should let it win over the global one")
    void mergeWithName_whenNamedConfigPicksProcessor_shouldLetItWinOverGlobalOne() {
        // given
        IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
        registry.register(CONFIG_NAME, IdempotencyConfig.builder()
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(ConflictConfig.disabled())
                .responseCache(ResponseCacheConfig.disabled())
                .build());
        DefaultOperationMetadataManager tested = manager(global(), registry);

        // when
        OperationMetadata result = tested.merge(raw().build(), CONFIG_NAME);

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
    }

    @Test
    @DisplayName("UT merge(name) when the call site goes transactional over a named config that handles conflicts should drop them")
    void mergeWithName_whenCallSiteGoesTransactionalOverNamedConfigThatHandlesConflicts_shouldDropThem() {
        // given
        IdempotencyConfigRegistry registry = new DefaultIdempotencyConfigRegistry();
        registry.register(CONFIG_NAME, IdempotencyConfig.builder()
                .conflict(ConflictConfig.wait(WaitConflictHandlerConfig.defaults()))
                .build());
        DefaultOperationMetadataManager tested = manager(global(), registry);

        // when
        OperationMetadata result = tested.merge(
                raw().processorType(ProcessorTypeToggle.TRANSACTIONAL).build(), CONFIG_NAME);

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
        assertThat(result.getConflictHandler()).isNull();
        assertThat(result.getResponseCacheConfig().isEnabled()).isFalse();
    }

    private static DefaultRawOperationMetadata.Builder raw() {
        return DefaultRawOperationMetadata.builder();
    }

    private static DefaultOperationMetadataManager manager(IdempotencyConfig global) {
        return manager(global, new DefaultIdempotencyConfigRegistry());
    }

    private static DefaultOperationMetadataManager manager(IdempotencyConfig global, IdempotencyConfigRegistry registry) {
        return new DefaultOperationMetadataManager(global, registry, conflictHandlerProvider(), fingerprintPolicyProvider());
    }

    /**
     * A global config an application using every feature would hand over: lock-based, so that conflict
     * handling and response caching are allowed beside fingerprinting.
     */
    private static IdempotencyConfig global() {
        return IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.wait(WaitConflictHandlerConfig.defaults()))
                .fingerprint(FingerprintConfig.builder()
                        .bodyHandleStrategy(BodyHandleStrategy.RAW_BYTES_HASH)
                        .build())
                .responseCache(ResponseCacheConfig.all())
                .build();
    }

    private static ConflictHandlerProvider conflictHandlerProvider() {
        return new RecordingConflictHandlerProvider();
    }

    /**
     * Stands in for the real provider so that a merge can be judged by the config it hands over, without
     * dragging a repository and a clock into a test about layering.
     */
    private static final class RecordingConflictHandlerProvider implements ConflictHandlerProvider {

        private ConflictConfig lastConfig;

        @Override
        public ConflictHandler provide(ConflictConfig config) {
            lastConfig = config;
            return config != null && config.isEnabled() ? new RejectConflictHandler() : null;
        }
    }

    private static FingerprintPolicyProvider fingerprintPolicyProvider() {
        return config -> config != null && config.isEnabled()
                ? new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback())
                : null;
    }

    /**
     * A raw metadata no builder would produce: the {@link DefaultRawOperationMetadata} one normalizes a blank
     * header and a negative ttl away, so only a hand-written one can ask whether the manager guards them too.
     */
    private static final class UnspecifiedRawOperationMetadata implements RawOperationMetadata {

        private final String headerName;
        private final Duration ttl;

        private UnspecifiedRawOperationMetadata(String headerName, Duration ttl) {
            this.headerName = headerName;
            this.ttl = ttl;
        }

        @Override
        public boolean useHeaderName() {
            return true;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public Duration getTtl() {
            return ttl;
        }

        @Override
        public ProcessorTypeToggle getProcessorType() {
            return ProcessorTypeToggle.UNSELECTED;
        }

        @Override
        public ConflictHandleStrategyToggle getConflictHandleStrategy() {
            return ConflictHandleStrategyToggle.UNSELECTED;
        }

        @Override
        public Toggle getFingerprintToggle() {
            return Toggle.UNSELECTED;
        }

        @Override
        public Toggle getCacheToggle() {
            return Toggle.UNSELECTED;
        }

        @Override
        public Toggle getCache4xxToggle() {
            return Toggle.UNSELECTED;
        }

        @Override
        public Toggle getCache5xxToggle() {
            return Toggle.UNSELECTED;
        }
    }

    /**
     * Stands in for the real provider so that a merge can be judged by the fingerprint config it hands over.
     */
    private static final class RecordingFingerprintPolicyProvider implements FingerprintPolicyProvider {

        private FingerprintConfig lastConfig;

        @Override
        public FingerprintPolicy provide(FingerprintConfig config) {
            lastConfig = config;
            return config != null && config.isEnabled()
                    ? new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback())
                    : null;
        }
    }
}
