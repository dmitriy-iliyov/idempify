package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyConfigUnitTest {

    @Test
    @DisplayName("UT build() when nothing is set should leave every section unspecified")
    void build_whenNothingIsSet_shouldLeaveEverySectionUnspecified() {
        // when
        IdempotencyConfig result = IdempotencyConfig.builder().build();

        // then
        assertThat(result.getHeaderName()).isNull();
        assertThat(result.getTtl()).isNull();
        assertThat(result.getConflictConfig()).isNull();
        assertThat(result.getFingerprintConfig()).isNull();
        assertThat(result.getResponseCacheConfig()).isNull();
    }

    @Test
    @DisplayName("UT build() when every section is set should carry them all")
    void build_whenEverySectionIsSet_shouldCarryThemAll() {
        // given
        ConflictConfig conflictConfig = ConflictConfig.reject();
        FingerprintConfig fingerprintConfig = FingerprintConfig.defaults();
        ResponseCacheConfig responseCacheConfig = ResponseCacheConfig.disabled();

        // when
        IdempotencyConfig result = IdempotencyConfig.builder()
                .headerName("X-Payment-Key")
                .ttl(Duration.ofHours(1))
                .conflict(conflictConfig)
                .fingerprint(fingerprintConfig)
                .responseCache(responseCacheConfig)
                .build();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(result.getConflictConfig()).isSameAs(conflictConfig);
        assertThat(result.getFingerprintConfig()).isSameAs(fingerprintConfig);
        assertThat(result.getResponseCacheConfig()).isSameAs(responseCacheConfig);
    }

    @Test
    @DisplayName("UT headerName() when the name is padded should strip it")
    void headerName_whenNameIsPadded_shouldStripIt() {
        assertThat(IdempotencyConfig.builder().headerName("  X-Payment-Key ").build().getHeaderName())
                .isEqualTo("X-Payment-Key");
    }

    @Test
    @DisplayName("UT headerName() when the name is blank should leave it unspecified")
    void headerName_whenNameIsBlank_shouldLeaveItUnspecified() {
        assertThat(IdempotencyConfig.builder().headerName("   ").build().getHeaderName()).isNull();
    }

    @Test
    @DisplayName("UT ttl() when the ttl is negative should throw IllegalArgumentException")
    void ttl_whenTtlIsNegative_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().ttl(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl cannot be negative");
    }

    @Test
    @DisplayName("UT ttl() when given an amount with a TimeUnit should convert it")
    void ttl_whenGivenAmountWithTimeUnit_shouldConvertIt() {
        assertThat(IdempotencyConfig.builder().ttl(30, TimeUnit.MINUTES).build().getTtl())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("UT ttl() when given an amount with a ChronoUnit should convert it")
    void ttl_whenGivenAmountWithChronoUnit_shouldConvertIt() {
        assertThat(IdempotencyConfig.builder().ttl(2, ChronoUnit.HOURS).build().getTtl())
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("UT ttl() when the unit is null should throw NullPointerException")
    void ttl_whenUnitIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().ttl(30, (TimeUnit) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("unit cannot be null");
    }

    @Test
    @DisplayName("UT fingerprint() when the section is null should throw NullPointerException")
    void fingerprint_whenSectionIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().fingerprint(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fingerprintConfig cannot be null");
    }

    @Test
    @DisplayName("UT responseCache() when built from a consumer should carry what the consumer set")
    void responseCache_whenBuiltFromConsumer_shouldCarryWhatConsumerSet() {
        // when
        IdempotencyConfig result = IdempotencyConfig.builder()
                .responseCache(builder -> builder.shouldCache4xx(false).shouldCache5xx(true))
                .build();

        // then
        assertThat(result.getResponseCacheConfig().shouldCache4xx()).isFalse();
        assertThat(result.getResponseCacheConfig().shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT notEmpty() when every section is specified should say so")
    void notEmpty_whenEverySectionIsSpecified_shouldSaySo() {
        assertThat(full().build().notEmpty()).isTrue();
    }

    @Test
    @DisplayName("UT notEmpty() when nothing is set should say the config is empty")
    void notEmpty_whenNothingIsSet_shouldSayConfigIsEmpty() {
        assertThat(IdempotencyConfig.builder().build().notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when the header name is missing should say the config is empty")
    void notEmpty_whenHeaderNameIsMissing_shouldSayConfigIsEmpty() {
        assertThat(full().headerName(null).build().notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when the ttl is missing should say the config is empty")
    void notEmpty_whenTtlIsMissing_shouldSayConfigIsEmpty() {
        assertThat(full().ttl(null).build().notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when the fingerprint section is missing should say the config is empty")
    void notEmpty_whenFingerprintSectionIsMissing_shouldSayConfigIsEmpty() {
        // given
        IdempotencyConfig tested = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.reject())
                .responseCache(ResponseCacheConfig.defaults())
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when a lock-based config carries no conflict section should say the config is empty")
    void notEmpty_whenLockBasedConfigCarriesNoConflictSection_shouldSayConfigIsEmpty() {
        // given
        IdempotencyConfig tested = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.defaults())
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when a lock-based config carries no response cache section should say the config is empty")
    void notEmpty_whenLockBasedConfigCarriesNoResponseCacheSection_shouldSayConfigIsEmpty() {
        // given
        IdempotencyConfig tested = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .fingerprint(FingerprintConfig.defaults())
                .conflict(ConflictConfig.reject())
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT notEmpty() when a transactional config carries no conflict or cache section should say it is not full")
    void notEmpty_whenTransactionalConfigCarriesNoConflictOrCacheSection_shouldSayItIsNotFull() {
        // given
        IdempotencyConfig tested = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .fingerprint(FingerprintConfig.defaults())
                .build();

        // when / then
        assertThat(tested.notEmpty()).isFalse();
        assertThat(transactional().build().notEmpty()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when the reference is empty should throw IllegalStateException")
    void merge_whenReferenceIsEmpty_shouldThrowIllegalStateException() {
        // given
        IdempotencyConfig reference = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .fingerprint(FingerprintConfig.defaults())
                .conflict(ConflictConfig.reject())
                .build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(reference, full().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reference must specify every setting a merge can fall back on");
    }

    @Test
    @DisplayName("UT merge() when the reference is null should throw NullPointerException")
    void merge_whenReferenceIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.merge(null, full().build()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target is null should throw NullPointerException")
    void merge_whenTargetIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.merge(full().build(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("target cannot be null");
    }

    @Test
    @DisplayName("UT merge() when the target names its own header and ttl should take them")
    void merge_whenTargetNamesItsOwnHeaderAndTtl_shouldTakeThem() {
        // given
        IdempotencyConfig target = IdempotencyConfig.builder()
                .headerName("X-Payment-Key")
                .ttl(Duration.ofMinutes(5))
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(full().build(), target);

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("UT merge() when the target says nothing should keep the reference untouched")
    void merge_whenTargetSaysNothing_shouldKeepReferenceUntouched() {
        // given
        IdempotencyConfig reference = full().build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(reference, IdempotencyConfig.builder().build());

        // then
        assertThat(result.getHeaderName()).isEqualTo(reference.getHeaderName());
        assertThat(result.getTtl()).isEqualTo(reference.getTtl());
        assertThat(result.getProcessorType()).isEqualTo(reference.getProcessorType());
        assertThat(result.getConflictConfig()).isEqualTo(reference.getConflictConfig());
        assertThat(result.getFingerprintConfig()).isEqualTo(reference.getFingerprintConfig());
        assertThat(result.getResponseCacheConfig()).isEqualTo(reference.getResponseCacheConfig());
    }

    @Test
    @DisplayName("UT merge() when the target header name is blank should keep the reference header")
    void merge_whenTargetHeaderNameIsBlank_shouldKeepReferenceHeader() {
        // given
        IdempotencyConfig target = IdempotencyConfig.builder().headerName("   ").build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(full().build(), target);

        // then
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
    }

    @Test
    @DisplayName("UT merge() when the target turns fingerprinting off should take that")
    void merge_whenTargetTurnsFingerprintingOff_shouldTakeThat() {
        // given
        IdempotencyConfig target = IdempotencyConfig.builder().fingerprint(FingerprintConfig.disabled()).build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(full().build(), target);

        // then
        assertThat(result.getFingerprintConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("UT merge() when the target tunes a section should layer it over the reference section")
    void merge_whenTargetTunesSection_shouldLayerItOverReferenceSection() {
        // given
        IdempotencyConfig reference = full()
                .responseCache(ResponseCacheConfig.all())
                .build();
        IdempotencyConfig target = IdempotencyConfig.builder()
                .responseCache(builder -> builder.shouldCache4xx(false))
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(reference, target);

        // then
        assertThat(result.getResponseCacheConfig().shouldCache4xx()).isFalse();
        assertThat(result.getResponseCacheConfig().shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT merge() when a transactional target meets a reference that handles conflicts should throw IllegalStateException")
    void merge_whenTransactionalTargetMeetsReferenceThatHandlesConflicts_shouldThrowIllegalStateException() {
        // given
        IdempotencyConfig target = IdempotencyConfig.builder()
                .processorType(ProcessorType.TRANSACTIONAL)
                .build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(full().build(), target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflictConfig cannot be enabled if processorType is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT merge() when the target of a transactional reference handles conflicts should throw IllegalStateException")
    void merge_whenTargetOfTransactionalReferenceHandlesConflicts_shouldThrowIllegalStateException() {
        // given
        IdempotencyConfig target = IdempotencyConfig.builder().conflict(ConflictConfig.reject()).build();

        // when / then
        assertThatThrownBy(() -> IdempotencyConfig.merge(transactional().build(), target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflictConfig cannot be enabled if processorType is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT toString() should name the header and the ttl it carries")
    void toString_shouldNameHeaderAndTtlItCarries() {
        assertThat(IdempotencyConfig.builder().headerName("X-Payment-Key").ttl(Duration.ofHours(1)).build().toString())
                .contains("headerName='X-Payment-Key'", "ttl=PT1H");
    }

    @Test
    @DisplayName("UT validate() when the processor was never decided should throw NullPointerException")
    void validate_whenProcessorWasNeverDecided_shouldThrowNullPointerException() {
        // given
        IdempotencyConfig config = IdempotencyConfig.builder().headerName("Idempotency-Key").build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processorType cannot be null");
    }

    @Test
    @DisplayName("UT validate() when a transactional config handles conflicts should throw IllegalStateException")
    void validate_whenTransactionalConfigHandlesConflicts_shouldThrowIllegalStateException() {
        // given
        IdempotencyConfig config = transactional().conflict(ConflictConfig.reject()).build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflictConfig cannot be enabled if processorType is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT validate() when a transactional config caches responses should throw IllegalStateException")
    void validate_whenTransactionalConfigCachesResponses_shouldThrowIllegalStateException() {
        // given
        IdempotencyConfig config = transactional().responseCache(ResponseCacheConfig.all()).build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("responseCacheConfig cannot be enabled if processorType is TRANSACTIONAL");
    }

    @Test
    @DisplayName("UT validate() when a transactional config turns both sections off should accept it")
    void validate_whenTransactionalConfigTurnsBothSectionsOff_shouldAcceptIt() {
        // given
        IdempotencyConfig config = transactional().build();

        // when / then
        config.validate();
    }

    @Test
    @DisplayName("UT validate() when the conflict section is incoherent should let the section refuse")
    void validate_whenConflictSectionIsIncoherent_shouldLetSectionRefuse() {
        // given
        IdempotencyConfig config = full()
                .conflict(ConflictConfig.builder()
                        .enabled(true)
                        .strategy(ConflictHandleStrategy.REJECT)
                        .handlerConfig(WaitConflictHandlerConfig.defaults())
                        .build())
                .build();

        // when / then
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConflictHandleStrategy must be WAIT");
    }

    @Test
    @DisplayName("UT processorType() when the type is null should throw NullPointerException")
    void processorType_whenTypeIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().processorType(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processorType cannot be null");
    }

    @Test
    @DisplayName("UT conflict() when the section is null should throw NullPointerException")
    void conflict_whenSectionIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().conflict(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("conflictConfig cannot be null");
    }

    @Test
    @DisplayName("UT responseCache() when the section is null should throw NullPointerException")
    void responseCache_whenSectionIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().responseCache((ResponseCacheConfig) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseCacheConfig cannot be null");
    }

    @Test
    @DisplayName("UT responseCache() when the consumer is null should throw NullPointerException")
    void responseCache_whenConsumerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> IdempotencyConfig.builder().responseCache((Consumer<ResponseCacheConfig.Builder>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("builderConsumer cannot be null");
    }

    @Test
    @DisplayName("UT ttl() when the ttl is null should leave it unspecified")
    void ttl_whenTtlIsNull_shouldLeaveItUnspecified() {
        // when
        IdempotencyConfig result = IdempotencyConfig.builder().ttl((Duration) null).build();

        // then
        assertThat(result.getTtl()).isNull();
    }

    @Test
    @DisplayName("UT builder(config) when the copy is tuned should leave the config it was taken from alone")
    void builderFromConfig_whenCopyIsTuned_shouldLeaveConfigItWasTakenFromAlone() {
        // given
        IdempotencyConfig config = full().build();

        // when
        IdempotencyConfig copy = IdempotencyConfig.builder(config)
                .headerName("X-Payment-Key")
                .ttl(Duration.ofMinutes(1))
                .build();

        // then
        assertThat(copy.getHeaderName()).isEqualTo("X-Payment-Key");
        assertThat(config.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(config.getTtl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("UT merge() when the target repeats every value of the reference should keep the reference untouched")
    void merge_whenTargetRepeatsEveryValueOfReference_shouldKeepReferenceUntouched() {
        // given
        IdempotencyConfig reference = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.disabled())
                .responseCache(ResponseCacheConfig.disabled())
                .build();

        // when
        IdempotencyConfig result = IdempotencyConfig.merge(reference, reference);

        // then
        assertThat(result.getHeaderName()).isEqualTo(reference.getHeaderName());
        assertThat(result.getTtl()).isEqualTo(reference.getTtl());
        assertThat(result.getProcessorType()).isEqualTo(reference.getProcessorType());
        assertThat(result.getConflictConfig()).isEqualTo(reference.getConflictConfig());
        assertThat(result.getResponseCacheConfig()).isEqualTo(reference.getResponseCacheConfig());
    }

    @Test
    @DisplayName("UT notEmpty() when a lock-based config has no conflict section should report it as incomplete")
    void notEmpty_whenLockBasedConfigHasNoConflictSection_shouldReportItAsIncomplete() {
        // given
        IdempotencyConfig config = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .fingerprint(FingerprintConfig.disabled())
                .build();

        // when / then
        assertThat(config.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT build() when nothing was told should decide nothing so every section is left to the layer below")
    void build_whenNothingWasTold_shouldDecideNothingSoEverySectionIsLeftToLayerBelow() {
        // when
        IdempotencyConfig result = IdempotencyConfig.builder().build();

        // then
        assertThat(result.getHeaderName()).isNull();
        assertThat(result.getTtl()).isNull();
        assertThat(result.getProcessorType()).isNull();
        assertThat(result.getConflictConfig()).isNull();
        assertThat(result.getFingerprintConfig()).isNull();
        assertThat(result.getResponseCacheConfig()).isNull();
        assertThat(result.notEmpty()).isFalse();
    }

    @Test
    @DisplayName("UT validate() when a transactional config leaves both optional sections out should accept it")
    void validate_whenTransactionalConfigLeavesBothOptionalSectionsOut_shouldAcceptIt() {
        // given
        IdempotencyConfig config = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .build();

        // when / then
        config.validate();
    }

    @Test
    @DisplayName("UT notEmpty() when the processor is missing should say the config is empty")
    void notEmpty_whenProcessorIsMissing_shouldSayConfigIsEmpty() {
        // given
        IdempotencyConfig config = IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.defaults())
                .build();

        // when / then
        assertThat(config.notEmpty()).isFalse();
    }

    /**
     * The least specific configuration source as a full application would hand it over: lock-based, so that
     * every section is allowed to be there.
     */
    private static IdempotencyConfig.Builder full() {
        return IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .conflict(ConflictConfig.reject())
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.defaults());
    }

    private static IdempotencyConfig.Builder transactional() {
        return IdempotencyConfig.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.TRANSACTIONAL)
                .conflict(ConflictConfig.disabled())
                .fingerprint(FingerprintConfig.defaults())
                .responseCache(ResponseCacheConfig.disabled());
    }
}
