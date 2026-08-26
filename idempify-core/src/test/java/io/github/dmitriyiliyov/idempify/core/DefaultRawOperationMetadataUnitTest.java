package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategyToggle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRawOperationMetadataUnitTest {

    @Test
    @DisplayName("UT build() when nothing is set should leave every attribute unspecified")
    void build_whenNothingIsSet_shouldLeaveEveryAttributeUnspecified() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder().build();

        // then
        assertThat(result.getHeaderName()).isNull();
        assertThat(result.getTtl()).isNull();
        assertThat(result.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategyToggle.UNSELECTED);
        assertThat(result.getFingerprintToggle()).isEqualTo(Toggle.UNSELECTED);
        assertThat(result.getCacheToggle()).isEqualTo(Toggle.UNSELECTED);
        assertThat(result.getCache4xxToggle()).isEqualTo(Toggle.UNSELECTED);
        assertThat(result.getCache5xxToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT build() when every attribute is set should carry them all")
    void build_whenEveryAttributeIsSet_shouldCarryThemAll() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .headerName("X-Payment-Key")
                .ttl(30)
                .timeUnit(TimeUnit.MINUTES)
                .processorType(ProcessorTypeToggle.TRANSACTIONAL)
                .conflictHandleStrategy(ConflictHandleStrategyToggle.WAIT)
                .fingerprintToggle(Toggle.ENABLE)
                .cacheToggle(Toggle.ENABLE)
                .cache4xxToggle(Toggle.DISABLE)
                .cache5xxToggle(Toggle.DISABLE)
                .build();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
        assertThat(result.getTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(result.getProcessorType()).isEqualTo(ProcessorTypeToggle.TRANSACTIONAL);
        assertThat(result.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategyToggle.WAIT);
        assertThat(result.getFingerprintToggle()).isEqualTo(Toggle.ENABLE);
        assertThat(result.getCacheToggle()).isEqualTo(Toggle.ENABLE);
        assertThat(result.getCache4xxToggle()).isEqualTo(Toggle.DISABLE);
        assertThat(result.getCache5xxToggle()).isEqualTo(Toggle.DISABLE);
    }

    @Test
    @DisplayName("UT useHeaderName() when the key source was never named should read the key from a header")
    void useHeaderName_whenKeySourceWasNeverNamed_shouldReadKeyFromHeader() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder().build();

        // then
        assertThat(result.useHeaderName()).isTrue();
    }

    @Test
    @DisplayName("UT useHeaderName() when the call site takes the key from an expression should say so")
    void useHeaderName_whenCallSiteTakesKeyFromExpression_shouldSaySo() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .useHeaderName(false)
                .build();

        // then
        assertThat(result.useHeaderName()).isFalse();
    }

    @Test
    @DisplayName("UT equals() when the key source differs should treat the metadata as different")
    void equals_whenKeySourceDiffers_shouldTreatMetadataAsDifferent() {
        // given
        RawOperationMetadata one = fullyConfigured().useHeaderName(true).build();
        RawOperationMetadata other = fullyConfigured().useHeaderName(false).build();

        // then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT headerName() when the name is padded should strip it")
    void headerName_whenNameIsPadded_shouldStripIt() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .headerName("  X-Payment-Key  ")
                .build();

        // then
        assertThat(result.getHeaderName()).isEqualTo("X-Payment-Key");
    }

    @Test
    @DisplayName("UT headerName() when the name is blank should leave it unspecified")
    void headerName_whenNameIsBlank_shouldLeaveItUnspecified() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .headerName("   ")
                .build();

        // then
        assertThat(result.getHeaderName()).isNull();
    }

    @Test
    @DisplayName("UT headerName() when the name is null should leave it unspecified")
    void headerName_whenNameIsNull_shouldLeaveItUnspecified() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .headerName(null)
                .build();

        // then
        assertThat(result.getHeaderName()).isNull();
    }

    @Test
    @DisplayName("UT ttl() when the amount is negative should leave the ttl unspecified")
    void ttl_whenAmountIsNegative_shouldLeaveTtlUnspecified() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .ttl(-5)
                .build();

        // then
        assertThat(result.getTtl()).isNull();
    }

    @Test
    @DisplayName("UT ttl() when the amount is zero should give a zero duration")
    void ttl_whenAmountIsZero_shouldGiveZeroDuration() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .ttl(0)
                .build();

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("UT ttl() when the time unit is set after the amount should still convert with that unit")
    void ttl_whenTimeUnitIsSetAfterAmount_shouldStillConvertWithThatUnit() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .ttl(2)
                .timeUnit(TimeUnit.DAYS)
                .build();

        // then
        assertThat(result.getTtl()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    @DisplayName("UT timeUnit() when the unit is null should throw NullPointerException")
    void timeUnit_whenUnitIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> DefaultRawOperationMetadata.builder().timeUnit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timeUnit cannot be null");
    }

    @Test
    @DisplayName("UT processorType() when the type is null should fall back to UNSELECTED")
    void processorType_whenTypeIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder().processorType(null).build();

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorTypeToggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT processorType() when never set should stay UNSELECTED so a broader layer can decide it")
    void processorType_whenNeverSet_shouldStayUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder().build();

        // then
        assertThat(result.getProcessorType()).isEqualTo(ProcessorTypeToggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT conflictHandleStrategy() when the strategy is null should fall back to UNSELECTED")
    void conflictHandleStrategy_whenStrategyIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .conflictHandleStrategy(null)
                .build();

        // then
        assertThat(result.getConflictHandleStrategy()).isEqualTo(ConflictHandleStrategyToggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT fingerprintToggle() when the toggle is null should fall back to UNSELECTED")
    void fingerprintToggle_whenToggleIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .fingerprintToggle(null)
                .build();

        // then
        assertThat(result.getFingerprintToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT cacheToggle() when the toggle is null should fall back to UNSELECTED")
    void cacheToggle_whenToggleIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .cacheToggle(null)
                .build();

        // then
        assertThat(result.getCacheToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT cache4xxToggle() when the toggle is null should fall back to UNSELECTED")
    void cache4xxToggle_whenToggleIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .cache4xxToggle(null)
                .build();

        // then
        assertThat(result.getCache4xxToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT cache5xxToggle() when the toggle is null should fall back to UNSELECTED")
    void cache5xxToggle_whenToggleIsNull_shouldFallBackToUnselected() {
        // when
        RawOperationMetadata result = DefaultRawOperationMetadata.builder()
                .cache5xxToggle(null)
                .build();

        // then
        assertThat(result.getCache5xxToggle()).isEqualTo(Toggle.UNSELECTED);
    }

    @Test
    @DisplayName("UT equals() when two metadata carry the same attributes should treat them as equal")
    void equals_whenTwoMetadataCarrySameAttributes_shouldTreatThemAsEqual() {
        // given
        RawOperationMetadata one = fullyConfigured().build();
        RawOperationMetadata other = fullyConfigured().build();

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the ttl differs should treat the metadata as different")
    void equals_whenTtlDiffers_shouldTreatMetadataAsDifferent() {
        // given
        RawOperationMetadata one = fullyConfigured().ttl(30).build();
        RawOperationMetadata other = fullyConfigured().ttl(60).build();

        // then
        assertThat(one).isNotEqualTo(other);
    }

    @Test
    @DisplayName("UT equals() when the processor type differs should treat the metadata as different")
    void equals_whenProcessorTypeDiffers_shouldTreatMetadataAsDifferent() {
        // given
        RawOperationMetadata one = fullyConfigured().processorType(ProcessorTypeToggle.TRANSACTIONAL).build();
        RawOperationMetadata other = fullyConfigured().processorType(ProcessorTypeToggle.LOCK_BASED).build();

        // then
        assertThat(one).isNotEqualTo(other);
        assertThat(one).doesNotHaveSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(DefaultRawOperationMetadata.builder().build()).isNotEqualTo("not a metadata");
    }

    @Test
    @DisplayName("UT toString() should name every attribute it carries")
    void toString_shouldNameEveryAttributeItCarries() {
        // when
        String result = fullyConfigured().build().toString();

        // then
        assertThat(result).contains(
                "headerName='X-Payment-Key'",
                "ttl=PT30M",
                "processorType=TRANSACTIONAL",
                "conflictHandleStrategy=WAIT",
                "fingerprintToggle=ENABLE",
                "cacheToggle=ENABLE",
                "cache4xxToggle=DISABLE",
                "cache5xxToggle=DISABLE"
        );
    }

    private DefaultRawOperationMetadata.Builder fullyConfigured() {
        return DefaultRawOperationMetadata.builder()
                .headerName("X-Payment-Key")
                .ttl(30)
                .timeUnit(TimeUnit.MINUTES)
                .processorType(ProcessorTypeToggle.TRANSACTIONAL)
                .conflictHandleStrategy(ConflictHandleStrategyToggle.WAIT)
                .fingerprintToggle(Toggle.ENABLE)
                .cacheToggle(Toggle.ENABLE)
                .cache4xxToggle(Toggle.DISABLE)
                .cache5xxToggle(Toggle.DISABLE);
    }

    @Test
    @DisplayName("UT equals() when compared with itself should be equal")
    void equals_whenComparedWithItself_shouldBeEqual() {
        RawOperationMetadata tested = DefaultRawOperationMetadata.builder().headerName("X-Key").build();
        assertThat(tested).isEqualTo(tested);
    }

    @Test
    @DisplayName("UT equals() when compared with null should not be equal")
    void equals_whenComparedWithNull_shouldNotBeEqual() {
        assertThat(DefaultRawOperationMetadata.builder().headerName("X-Key").build()).isNotEqualTo(null);
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(DefaultRawOperationMetadata.builder().headerName("X-Key").build()).isNotEqualTo("not a config");
    }

    @Test
    @DisplayName("UT hashCode() when two are equal should agree")
    void hashCode_whenTwoAreEqual_shouldAgree() {
        assertThat(DefaultRawOperationMetadata.builder().headerName("X-Key").build()).hasSameHashCodeAs(DefaultRawOperationMetadata.builder().headerName("X-Key").build());
    }

    @Test
    @DisplayName("UT equals() when the ttls differ should tell the metadata apart")
    void equals_whenTtlsDiffer_shouldTellMetadataApart() {
        assertThat(DefaultRawOperationMetadata.builder().ttl(1).build())
                .isNotEqualTo(DefaultRawOperationMetadata.builder().ttl(2).build());
    }
}
