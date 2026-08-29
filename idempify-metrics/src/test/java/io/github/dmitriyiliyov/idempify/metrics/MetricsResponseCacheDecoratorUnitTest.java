package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.DefaultCachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Counting a lookup must not change what the lookup answers: the decorator is judged both on the numbers it
 * reports and on the response reaching the caller untouched.
 */
class MetricsResponseCacheDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String METER = "idempify.cache.gets";
    private static final String TAG = "result";
    private static final String HIT = "hit";
    private static final String MISS = "miss";

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("UT constructor() when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new MetricsResponseCacheDecorator(null, registry))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when registry is null should throw NullPointerException")
    void constructor_whenRegistryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new MetricsResponseCacheDecorator(new RecordingCache(null), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("registry cannot be null");
    }

    @Test
    @DisplayName("UT constructor() should register both results before the first lookup")
    void constructor_shouldRegisterBothResultsBeforeFirstLookup() {
        // when
        new MetricsResponseCacheDecorator(new RecordingCache(null), registry);

        // then
        assertThat(count(HIT)).isZero();
        assertThat(count(MISS)).isZero();
    }

    @Test
    @DisplayName("UT constructor() should keep both results in one meter named under the library")
    void constructor_shouldKeepBothResultsInOneMeterNamedUnderLibrary() {
        // when
        new MetricsResponseCacheDecorator(new RecordingCache(null), registry);

        // then
        assertThat(METER).startsWith("idempify.");
        assertThat(METER).doesNotContain("count");
        assertThat(registry.get(METER).counters()).hasSize(2);
        assertThat(registry.get(METER).counter().getId().getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the cache answers should count a hit and hand the response back")
    void findByIdempotencyKey_whenCacheAnswers_shouldCountHitAndHandResponseBack() {
        // given
        CachedResponse stored = response();
        RecordingCache delegate = new RecordingCache(stored);
        ResponseCache tested = new MetricsResponseCacheDecorator(delegate, registry);

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isSameAs(stored);
        assertThat(delegate.lastFoundKey).isEqualTo(KEY);
        assertThat(count(HIT)).isEqualTo(1.0);
        assertThat(count(MISS)).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the cache has nothing should count a miss and report it unchanged")
    void findByIdempotencyKey_whenCacheHasNothing_shouldCountMissAndReportItUnchanged() {
        // given
        ResponseCache tested = new MetricsResponseCacheDecorator(new RecordingCache(null), registry);

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isNull();
        assertThat(count(MISS)).isEqualTo(1.0);
        assertThat(count(HIT)).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when lookups repeat should add each outcome to its own counter")
    void findByIdempotencyKey_whenLookupsRepeat_shouldAddEachOutcomeToItsOwnCounter() {
        // given
        SwitchableCache delegate = new SwitchableCache();
        ResponseCache tested = new MetricsResponseCacheDecorator(delegate, registry);

        // when
        delegate.answer = response();
        tested.findByIdempotencyKey(KEY);
        tested.findByIdempotencyKey(KEY);
        delegate.answer = null;
        tested.findByIdempotencyKey(KEY);

        // then
        assertThat(count(HIT)).isEqualTo(2.0);
        assertThat(count(MISS)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("UT save() should reach the delegate untouched and count nothing")
    void save_shouldReachDelegateUntouchedAndCountNothing() {
        // given
        RecordingCache delegate = new RecordingCache(null);
        ResponseCache tested = new MetricsResponseCacheDecorator(delegate, registry);
        CachedResponse response = response();

        // when
        tested.save(KEY, response, Duration.ofMinutes(5));

        // then
        assertThat(delegate.lastSavedKey).isEqualTo(KEY);
        assertThat(delegate.lastSavedResponse).isSameAs(response);
        assertThat(delegate.lastSavedTtl).isEqualTo(Duration.ofMinutes(5));
        assertThat(count(HIT)).isZero();
        assertThat(count(MISS)).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the delegate throws should let the failure through uncounted")
    void findByIdempotencyKey_whenDelegateThrows_shouldLetFailureThroughUncounted() {
        // given
        ResponseCache tested = new MetricsResponseCacheDecorator(new ThrowingCache(), registry);

        // when / then
        assertThatThrownBy(() -> tested.findByIdempotencyKey(KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis is down");
        assertThat(count(HIT)).isZero();
        assertThat(count(MISS)).isZero();
    }

    private double count(String result) {
        return registry.get(METER).tag(TAG, result).counter().count();
    }

    private static CachedResponse response() {
        return new DefaultCachedResponse(201, new byte[]{1}, "application/json", "fingerprint");
    }

    private static class RecordingCache implements ResponseCache {

        private final CachedResponse answer;
        private UUID lastFoundKey;
        private UUID lastSavedKey;
        private CachedResponse lastSavedResponse;
        private Duration lastSavedTtl;

        private RecordingCache(CachedResponse answer) {
            this.answer = answer;
        }

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            lastFoundKey = idempotencyKey;
            return answer;
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
            lastSavedKey = idempotencyKey;
            lastSavedResponse = response;
            lastSavedTtl = ttl;
        }
    }

    private static final class SwitchableCache implements ResponseCache {

        private CachedResponse answer;

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            return answer;
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) { }
    }

    private static final class ThrowingCache implements ResponseCache {

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            throw new IllegalStateException("redis is down");
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) { }
    }
}
