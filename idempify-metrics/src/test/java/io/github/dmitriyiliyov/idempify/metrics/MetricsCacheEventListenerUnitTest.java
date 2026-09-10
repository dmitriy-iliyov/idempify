package io.github.dmitriyiliyov.idempify.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The listener only counts, so what is judged here is the shape of the meter a dashboard will query: one
 * counter split by what the lookup found, registered whether or not anything happened yet.
 */
class MetricsCacheEventListenerUnitTest {

    private static final String METER = "idempify.cache.gets";
    private static final String TAG = "result";
    private static final String HIT = "hit";
    private static final String MISS = "miss";

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("UT constructor() when registry is null should throw NullPointerException")
    void constructor_whenRegistryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new MetricsCacheEventListener(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("registry cannot be null");
    }

    @Test
    @DisplayName("UT constructor() should register both counters before anything is counted")
    void constructor_shouldRegisterBothCountersBeforeAnythingIsCounted() {
        // when
        new MetricsCacheEventListener(registry);

        // then
        assertThat(count(HIT)).isZero();
        assertThat(count(MISS)).isZero();
    }

    @Test
    @DisplayName("UT onHit() should count a hit and leave the miss counter alone")
    void onHit_shouldCountHitAndLeaveMissCounterAlone() {
        // given
        MetricsCacheEventListener tested = new MetricsCacheEventListener(registry);

        // when
        tested.onHit();

        // then
        assertThat(count(HIT)).isEqualTo(1);
        assertThat(count(MISS)).isZero();
    }

    @Test
    @DisplayName("UT onMiss() should count a miss and leave the hit counter alone")
    void onMiss_shouldCountMissAndLeaveHitCounterAlone() {
        // given
        MetricsCacheEventListener tested = new MetricsCacheEventListener(registry);

        // when
        tested.onMiss();

        // then
        assertThat(count(MISS)).isEqualTo(1);
        assertThat(count(HIT)).isZero();
    }

    @Test
    @DisplayName("UT onHit() when called repeatedly should keep counting on the same meter")
    void onHit_whenCalledRepeatedly_shouldKeepCountingOnSameMeter() {
        // given
        MetricsCacheEventListener tested = new MetricsCacheEventListener(registry);

        // when
        tested.onHit();
        tested.onHit();
        tested.onHit();

        // then
        assertThat(count(HIT)).isEqualTo(3);
        assertThat(registry.find(METER).counters()).hasSize(2);
    }

    @Test
    @DisplayName("UT counters should carry a description so a dashboard can name them")
    void counters_shouldCarryDescriptionSoDashboardCanNameThem() {
        // given
        new MetricsCacheEventListener(registry);

        // when / then
        assertThat(registry.find(METER).tag(TAG, HIT).counter().getId().getDescription())
                .isEqualTo("Operation cache lookups, by what they found");
    }

    private double count(String result) {
        return registry.find(METER).tag(TAG, result).counter().count();
    }
}
