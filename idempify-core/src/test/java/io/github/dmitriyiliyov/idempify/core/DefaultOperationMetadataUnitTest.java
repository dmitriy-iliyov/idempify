package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;
import io.github.dmitriyiliyov.idempify.core.conflict.RejectConflictHandler;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.RawHashingFingerprintPolicy;
import io.github.dmitriyiliyov.idempify.core.fingerprint.ThrowingEmptyBodyFallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This is the end of the layering, so {@code build()} must refuse anything a processor would still have to
 * decide for itself. Two settings are exempt on purpose: a call site may legitimately neither fingerprint nor
 * handle conflicts, and for those "absent" is the answer rather than an unfinished decision.
 */
class DefaultOperationMetadataUnitTest {

    @Test
    @DisplayName("UT build() when every setting is decided should carry them all")
    void build_whenEverySettingIsDecided_shouldCarryThemAll() {
        // given
        ConflictHandler handler = new RejectConflictHandler();
        FingerprintPolicy policy = policy();

        // when
        OperationMetadata result = full()
                .conflictHandler(handler)
                .fingerprintPolicy(policy)
                .build();

        // then
        assertThat(result.getHeaderName()).isEqualTo("Idempotency-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.LOCK_BASED);
        assertThat(result.getConflictHandler()).isSameAs(handler);
        assertThat(result.getFingerprintPolicy()).isSameAs(policy);
        assertThat(result.getResponseConfig()).isNotNull();
    }

    @Test
    @DisplayName("UT build() when the header name was never decided should report that no header is read")
    void build_whenHeaderNameWasNeverDecided_shouldReportThatNoHeaderIsRead() {
        // when
        OperationMetadata result = DefaultOperationMetadata.builder()
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.LOCK_BASED)
                .responseConfig(ResponseConfig.disabled())
                .build();

        // then
        assertThat(result.getHeaderName()).isNull();
        assertThat(result.useHeaderName())
                .describedAs("a call site taking its key from an expression resolves to metadata without a header")
                .isFalse();
    }

    @Test
    @DisplayName("UT build() when the ttl was never decided should refuse to build")
    void build_whenTtlWasNeverDecided_shouldRefuseToBuild() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .processorType(ProcessorType.LOCK_BASED)
                .responseConfig(ResponseConfig.disabled())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ttl cannot be null");
    }

    @Test
    @DisplayName("UT build() when the processor was never decided should refuse to build")
    void build_whenProcessorWasNeverDecided_shouldRefuseToBuild() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(1))
                .responseConfig(ResponseConfig.disabled())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("processorType cannot be null");
    }

    @Test
    @DisplayName("UT build() when the response cache was never decided should throw NullPointerException")
    void build_whenResponseCacheWasNeverDecided_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.LOCK_BASED)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseConfig cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("UT headerName() when the name says nothing should refuse it rather than carry a blank")
    void headerName_whenNameSaysNothing_shouldRefuseItRatherThanCarryBlank(String headerName) {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder().headerName(headerName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headerName cannot be blank");
    }

    @Test
    @DisplayName("UT headerName() when the name is padded should strip it so the header is looked up by its real name")
    void headerName_whenNameIsPadded_shouldStripIt() {
        // when
        OperationMetadata result = full().headerName("  X-Payment-Key  ").build();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
    }

    @Test
    @DisplayName("UT ttl() when the ttl is negative should refuse it rather than carry an already-expired operation")
    void ttl_whenTtlIsNegative_shouldRefuseItRatherThanCarryAlreadyExpiredOperation() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder().ttl(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl cannot be negative");
    }

    @Test
    @DisplayName("UT useFingerprint() when a policy was resolved should say the call site fingerprints")
    void useFingerprint_whenPolicyWasResolved_shouldSayCallSiteFingerprints() {
        // when
        OperationMetadata result = full().fingerprintPolicy(policy()).build();

        // then
        assertThat(result.useFingerprint()).isTrue();
    }

    @Test
    @DisplayName("UT useFingerprint() when no policy was resolved should say the call site does not fingerprint")
    void useFingerprint_whenNoPolicyWasResolved_shouldSayCallSiteDoesNotFingerprint() {
        // when
        OperationMetadata result = full().build();

        // then
        assertThat(result.useFingerprint()).isFalse();
        assertThat(result.getFingerprintPolicy()).isNull();
    }

    @Test
    @DisplayName("UT build() when conflicts are not handled should allow the handler to stay absent")
    void build_whenConflictsAreNotHandled_shouldAllowHandlerToStayAbsent() {
        // when
        OperationMetadata result = full().build();

        // then
        assertThat(result.getConflictHandler()).isNull();
    }

    @Test
    @DisplayName("UT useConflictHandler() when a handler was resolved should say the call site hands conflicts over")
    void useConflictHandler_whenHandlerWasResolved_shouldSayCallSiteHandsConflictsOver() {
        // when
        OperationMetadata result = full().conflictHandler(new RejectConflictHandler()).build();

        // then
        assertThat(result.useConflictHandler()).isTrue();
    }

    @Test
    @DisplayName("UT useConflictHandler() when no handler was resolved should say the call site handles them itself")
    void useConflictHandler_whenNoHandlerWasResolved_shouldSayCallSiteHandlesThemItself() {
        // when
        OperationMetadata result = full().build();

        // then
        assertThat(result.useConflictHandler()).isFalse();
        assertThat(result.getConflictHandler()).isNull();
    }

    @Test
    @DisplayName("UT build() when the processor is TRANSACTIONAL and caching is on should refuse the pair")
    void build_whenProcessorIsTransactionalAndCachingIsOn_shouldRefusePair() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.TRANSACTIONAL)
                .responseConfig(ResponseConfig.all())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("responses with 4xx or 5xx cannot be cached");
    }

    @Test
    @DisplayName("UT build() when the processor is TRANSACTIONAL and only 5xx is kept should refuse the pair")
    void build_whenProcessorIsTransactionalAndOnly5xxIsKept_shouldRefusePair() {
        assertThatThrownBy(() -> DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.TRANSACTIONAL)
                .responseConfig(ResponseConfig.builder().shouldCache4xx(false).shouldCache5xx(true).build())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("responses with 4xx or 5xx cannot be cached");
    }

    @Test
    @DisplayName("UT build() when the processor is TRANSACTIONAL and caching is off should accept the pair")
    void build_whenProcessorIsTransactionalAndCachingIsOff_shouldAcceptPair() {
        // when
        OperationMetadata result = DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.TRANSACTIONAL)
                .responseConfig(ResponseConfig.disabled())
                .build();

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorType.TRANSACTIONAL);
        assertThat(result.getResponseConfig().shouldCache4xx()).isFalse();
    }

    private static DefaultOperationMetadata.Builder full() {
        return DefaultOperationMetadata.builder()
                .headerName("Idempotency-Key")
                .ttl(Duration.ofHours(24))
                .processorType(ProcessorType.LOCK_BASED)
                .responseConfig(ResponseConfig.all());
    }

    private static FingerprintPolicy policy() {
        return new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        // when
        String result = full().build().toString();

        // then
        assertThat(result).contains("headerName='Idempotency-Key'", "ttl=PT24H", "processorType=LOCK_BASED");
    }
}
