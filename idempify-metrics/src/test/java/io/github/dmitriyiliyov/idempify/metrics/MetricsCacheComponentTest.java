package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.cache.InMemoryCacheResponseRepositoryDecorator;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counters over a cache that really caches: the listener this module contributes is taken out of its own
 * context and handed to a live {@link InMemoryCacheResponseRepositoryDecorator}, so what a dashboard would
 * show is read off genuine lookups rather than off calls a test made itself. Only the store behind the cache
 * is stood in for.
 * <p>
 * This is also the only place where the meaning the {@code CacheEventListener} contract gives {@code hit} -
 * a lookup the cache was able to answer - is checked against what the cache actually does with an entry that
 * has outlived the record it stands for.
 */
class MetricsCacheComponentTest {

    private static final String METER = "idempify.cache.gets";
    private static final String TAG = "result";
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MovingClock clock = new MovingClock(NOW);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyMetricsAutoConfiguration.class))
            .withPropertyValues("idempify.metrics.enabled=true")
            .withBean(MeterRegistry.class, () -> registry);

    @Test
    @DisplayName("CT findByIdempotencyKey() when the key repeats should count a miss and then a hit")
    void findByIdempotencyKey_whenKeyRepeats_shouldCountMissAndThenHit() {
        contextRunner.run(context -> {
            // given
            RecordingRepository repository = new RecordingRepository(container(NOW.plus(TTL)));
            ResponseRepository tested = cacheOver(repository, context.getBean(CacheEventListener.class));

            // when
            tested.findByIdempotencyKey(KEY);
            tested.findByIdempotencyKey(KEY);

            // then
            assertThat(count("miss")).isEqualTo(1);
            assertThat(count("hit")).isEqualTo(1);
            assertThat(repository.keys).containsExactly(KEY);
        });
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() when the store has nothing should count every lookup as a miss")
    void findByIdempotencyKey_whenStoreHasNothing_shouldCountEveryLookupAsMiss() {
        contextRunner.run(context -> {
            // given
            RecordingRepository repository = new RecordingRepository(null);
            ResponseRepository tested = cacheOver(repository, context.getBean(CacheEventListener.class));

            // when
            tested.findByIdempotencyKey(KEY);
            tested.findByIdempotencyKey(KEY);

            // then
            assertThat(count("miss")).isEqualTo(2);
            assertThat(count("hit")).isZero();
            assertThat(repository.keys).containsExactly(KEY, KEY);
        });
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() when the cached entry has outlived the record should count a miss, not a hit")
    void findByIdempotencyKey_whenCachedEntryHasOutlivedRecord_shouldCountMissNotHit() {
        contextRunner.run(context -> {
            // given
            RecordingRepository repository = new RecordingRepository(container(NOW.plus(TTL)));
            ResponseRepository tested = cacheOver(repository, context.getBean(CacheEventListener.class));
            tested.findByIdempotencyKey(KEY);

            // when
            clock.moveTo(NOW.plus(TTL).plusSeconds(1));
            tested.findByIdempotencyKey(KEY);

            // then
            assertThat(count("hit")).isZero();
            assertThat(count("miss")).isEqualTo(2);
            assertThat(repository.keys).containsExactly(KEY, KEY);
        });
    }

    @Test
    @DisplayName("CT save() should put the response where the next lookup counts a hit")
    void save_shouldPutResponseWhereNextLookupCountsHit() {
        contextRunner.run(context -> {
            // given
            RecordingRepository repository = new RecordingRepository(null);
            ResponseRepository tested = cacheOver(repository, context.getBean(CacheEventListener.class));

            // when
            tested.save(KEY, "raw-response");
            Optional<RawResponseContainer> replayed = tested.findByIdempotencyKey(KEY);

            // then
            assertThat(replayed).isPresent();
            assertThat(count("hit")).isEqualTo(1);
            assertThat(count("miss")).isZero();
            assertThat(repository.keys).isEmpty();
        });
    }

    private ResponseRepository cacheOver(ResponseRepository repository, CacheEventListener listener) {
        return new InMemoryCacheResponseRepositoryDecorator(repository, 8, clock, listener);
    }

    private double count(String result) {
        return registry.get(METER).tag(TAG, result).counter().count();
    }

    private static RawResponseContainer container(Instant expiresAt) {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", expiresAt);
    }

    /**
     * Stands in for the store the cache sits in front of, writing down every key that reached it so a test
     * can tell a lookup the cache answered from one that went past it.
     */
    private static final class RecordingRepository implements ResponseRepository {

        private final Map<UUID, RawResponseContainer> containers = new LinkedHashMap<>();
        private final List<UUID> keys = new ArrayList<>();

        private RecordingRepository(RawResponseContainer container) {
            if (container != null) {
                containers.put(KEY, container);
            }
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            RawResponseContainer saved = container(NOW.plus(TTL));
            containers.put(idempotencyKey, saved);
            return saved;
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            keys.add(idempotencyKey);
            return Optional.ofNullable(containers.get(idempotencyKey));
        }
    }

    /**
     * A clock a test can push forward, so an entry can outlive its record without anything waiting for it.
     */
    private static final class MovingClock extends Clock {

        private Instant instant;

        private MovingClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
