package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.DefaultCachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module against a real Redis: live auto-configuration, live template with its own serializers, live
 * {@link RedisResponseCache}. Only {@link CachePropertiesHolder} is stood in for - it belongs to the core.
 * <p>
 * Needs Docker, like the postgres integration test.
 */
@Testcontainers
class IdempifyRedisCacheComponentTest {

    private static final String CACHE_NAME = "idempify";
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_IDEMPOTENCY_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, IdempifyRedisCacheComponentTest::connectionFactory)
            .withBean(CachePropertiesHolder.class, () -> () -> CACHE_NAME);

    @BeforeEach
    void setUp() throws Exception {
        redis.execInContainer("redis-cli", "FLUSHALL");
    }

    @Test
    @DisplayName("CT save() then findByIdempotencyKey() with the same key should return the stored response")
    void save_thenFindByIdempotencyKey_shouldReturnStoredResponse() {
        // given
        CachedResponse stored = cachedResponse(201, "{\"id\":1}");

        // when / then
        runner.run(context -> {
            ResponseCache tested = context.getBean(ResponseCache.class);
            tested.save(IDEMPOTENCY_KEY, stored, Duration.ofMinutes(10));

            CachedResponse replayed = tested.findByIdempotencyKey(IDEMPOTENCY_KEY);

            assertThat(replayed).isNotNull();
            assertThat(replayed.getStatus()).isEqualTo(201);
            assertThat(replayed.getBody()).isEqualTo(stored.getBody());
            assertThat(replayed.getContentType()).isEqualTo("application/json");
            assertThat(replayed.getFingerprint()).isEqualTo("fingerprint");
        });
    }

    @Test
    @DisplayName("CT findByIdempotencyKey() when nothing was stored should return null")
    void findByIdempotencyKey_whenNothingWasStored_shouldReturnNull() {
        // when / then
        runner.run(context -> {
            ResponseCache tested = context.getBean(ResponseCache.class);

            assertThat(tested.findByIdempotencyKey(UUID.randomUUID())).isNull();
        });
    }

    @Test
    @DisplayName("CT save() should let the entry expire with the given ttl")
    void save_shouldLetTheEntryExpireWithGivenTtl() {
        // when / then
        runner.run(context -> {
            ResponseCache tested = context.getBean(ResponseCache.class);
            @SuppressWarnings("unchecked")
            RedisTemplate<String, CachedResponse> template =
                    context.getBean("idempifyRedisTemplate", RedisTemplate.class);
            tested.save(IDEMPOTENCY_KEY, cachedResponse(201, "{\"id\":1}"), Duration.ofMinutes(10));

            Long expire = template.getExpire("%s:%s".formatted(CACHE_NAME, IDEMPOTENCY_KEY), TimeUnit.SECONDS);

            assertThat(expire).isNotNull().isPositive().isLessThanOrEqualTo(600);
        });
    }

    @Test
    @DisplayName("CT save() with two keys should replay each response under its own key")
    void save_withTwoKeys_shouldReplayEachResponseUnderItsOwnKey() {
        // given
        CachedResponse first = cachedResponse(201, "{\"id\":1}");
        CachedResponse second = cachedResponse(202, "{\"id\":2}");

        // when / then
        runner.run(context -> {
            ResponseCache tested = context.getBean(ResponseCache.class);
            tested.save(IDEMPOTENCY_KEY, first, Duration.ofMinutes(10));
            tested.save(OTHER_IDEMPOTENCY_KEY, second, Duration.ofMinutes(10));

            assertThat(tested.findByIdempotencyKey(IDEMPOTENCY_KEY).getStatus()).isEqualTo(201);
            assertThat(tested.findByIdempotencyKey(OTHER_IDEMPOTENCY_KEY).getStatus()).isEqualTo(202);
        });
    }

    @Test
    @DisplayName("CT save() when ttl is zero should store nothing")
    void save_whenTtlIsZero_shouldStoreNothing() {
        // when / then
        runner.run(context -> {
            ResponseCache tested = context.getBean(ResponseCache.class);

            tested.save(IDEMPOTENCY_KEY, cachedResponse(201, "{\"id\":1}"), Duration.ZERO);

            assertThat(tested.findByIdempotencyKey(IDEMPOTENCY_KEY)).isNull();
        });
    }

    private static RedisConnectionFactory connectionFactory() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private CachedResponse cachedResponse(int status, String body) {
        return new DefaultCachedResponse(
                status,
                body.getBytes(StandardCharsets.UTF_8),
                "application/json",
                "fingerprint"
        );
    }
}
