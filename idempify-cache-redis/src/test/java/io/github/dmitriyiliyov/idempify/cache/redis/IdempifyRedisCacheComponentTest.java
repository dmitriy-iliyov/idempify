package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module against a real Redis: live auto-configuration, live template with its own serializers, live
 * {@link RedisCacheResponseRepositoryDecorator}. Only what belongs to the core is stood in for - the
 * repository behind the cache and the holder of the {@code idempify.cache.*} properties.
 * <p>
 * Needs Docker, like the postgres integration test.
 */
@Testcontainers
class IdempifyRedisCacheComponentTest {

    private static final String CACHE_NAME = "idempify";
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_IDEMPOTENCY_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
            .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.type=DISTRIBUTED")
            .withBean(RedisConnectionFactory.class, IdempifyRedisCacheComponentTest::connectionFactory)
            .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
            .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
            .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder(CACHE_NAME));

    @BeforeEach
    void setUp() throws Exception {
        redis.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() when the key repeats should answer from redis without reaching the repository")
    void findByIdempotencyKey_whenKeyRepeats_shouldAnswerFromRedisWithoutReachingRepository() {
        // when / then
        runner.run(context -> {
            RecordingRepository repository = new RecordingRepository(container(IDEMPOTENCY_KEY, "raw-response"));
            ResponseRepository tested = context.getBean(ResponseRepositoryWrapper.class).wrap(repository);

            tested.findByIdempotencyKey(IDEMPOTENCY_KEY);
            Optional<RawResponseContainer> replayed = tested.findByIdempotencyKey(IDEMPOTENCY_KEY);

            assertThat(replayed).isPresent();
            assertThat(replayed.get().getResponse()).isEqualTo("raw-response");
            assertThat(replayed.get().getFingerprint()).isEqualTo("fingerprint");
            assertThat(replayed.get().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
            assertThat(repository.keys).containsExactly(IDEMPOTENCY_KEY);
        });
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() when the repository has nothing should keep answering empty")
    void findByIdempotencyKey_whenRepositoryHasNothing_shouldKeepAnsweringEmpty() {
        // when / then
        runner.run(context -> {
            RecordingRepository repository = new RecordingRepository();
            ResponseRepository tested = context.getBean(ResponseRepositoryWrapper.class).wrap(repository);

            assertThat(tested.findByIdempotencyKey(IDEMPOTENCY_KEY)).isEmpty();
            assertThat(tested.findByIdempotencyKey(IDEMPOTENCY_KEY)).isEmpty();
            assertThat(repository.keys).containsExactly(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY);
        });
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() with two keys should answer each under its own key")
    void findByIdempotencyKey_withTwoKeys_shouldAnswerEachUnderItsOwnKey() {
        // when / then
        runner.run(context -> {
            RecordingRepository repository = new RecordingRepository(
                    container(IDEMPOTENCY_KEY, "one"), container(OTHER_IDEMPOTENCY_KEY, "two"));
            ResponseRepository tested = context.getBean(ResponseRepositoryWrapper.class).wrap(repository);

            tested.findByIdempotencyKey(IDEMPOTENCY_KEY);
            tested.findByIdempotencyKey(OTHER_IDEMPOTENCY_KEY);

            assertThat(tested.findByIdempotencyKey(IDEMPOTENCY_KEY).orElseThrow().getResponse())
                    .isEqualTo("one");
            assertThat(tested.findByIdempotencyKey(OTHER_IDEMPOTENCY_KEY).orElseThrow().getResponse())
                    .isEqualTo("two");
        });
    }

    private static RedisConnectionFactory connectionFactory() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private static RawResponseContainer container(UUID idempotencyKey, String response) {
        return new DefaultRawResponseContainer(response, "fingerprint", NOW.plus(Duration.ofHours(1)));
    }

    /**
     * Stands in for the store the cache sits in front of, writing down every key that reached it so a test
     * can tell a redis hit from a trip to the database.
     */
    private static final class RecordingRepository implements ResponseRepository {

        private final Map<UUID, RawResponseContainer> containers = new LinkedHashMap<>();
        private final List<UUID> keys = new ArrayList<>();

        private RecordingRepository(RawResponseContainer... containers) {
            UUID[] under = {IDEMPOTENCY_KEY, OTHER_IDEMPOTENCY_KEY};
            for (int i = 0; i < containers.length; i++) {
                if (containers[i] != null) {
                    this.containers.put(under[i], containers[i]);
                }
            }
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            RawResponseContainer saved =
                    new DefaultRawResponseContainer(response, "fingerprint", NOW.plus(Duration.ofHours(1)));
            containers.put(idempotencyKey, saved);
            return saved;
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            keys.add(idempotencyKey);
            return Optional.ofNullable(containers.get(idempotencyKey));
        }
    }
}
