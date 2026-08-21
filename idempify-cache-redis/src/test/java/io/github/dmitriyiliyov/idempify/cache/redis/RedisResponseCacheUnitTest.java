package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.DefaultCachedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisResponseCacheUnitTest {

    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CACHE_NAME = "idempify";
    private static final String EXPECTED_KEY = "idempify:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private RedisTemplate<String, CachedResponse> redisTemplate;
    @Mock
    private ValueOperations<String, CachedResponse> valueOperations;
    private RedisResponseCache tested;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tested = new RedisResponseCache(redisTemplate, CACHE_NAME);
    }

    @Test
    @DisplayName("UT constructor when redisTemplate is null should throw NullPointerException")
    void constructor_whenRedisTemplateIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RedisResponseCache(null, CACHE_NAME))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("redisTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor when cacheName is null should throw NullPointerException")
    void constructor_whenCacheNameIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new RedisResponseCache(redisTemplate, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cacheName cannot be null");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when key is null should return null without touching redis")
    void findByIdempotencyKey_whenKeyIsNull_shouldReturnNullWithoutTouchingRedis() {
        // when
        CachedResponse result = tested.findByIdempotencyKey(null);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when entry exists should read it by the cache-name prefixed key")
    void findByIdempotencyKey_whenEntryExists_shouldReadItByCacheNamePrefixedKey() {
        // given
        CachedResponse stored = cachedResponse();
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(stored);

        // when
        CachedResponse result = tested.findByIdempotencyKey(IDEMPOTENCY_KEY);

        // then
        assertThat(result).isSameAs(stored);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when entry is absent should return null")
    void findByIdempotencyKey_whenEntryIsAbsent_shouldReturnNull() {
        // given
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

        // when
        CachedResponse result = tested.findByIdempotencyKey(IDEMPOTENCY_KEY);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT save() when arguments are valid should write by the cache-name prefixed key with the given ttl")
    void save_whenArgumentsAreValid_shouldWriteByCacheNamePrefixedKeyWithGivenTtl() {
        // given
        CachedResponse response = cachedResponse();

        // when
        tested.save(IDEMPOTENCY_KEY, response, TTL);

        // then
        verify(valueOperations).set(EXPECTED_KEY, response, TTL);
    }

    @Test
    @DisplayName("UT save() when key is null should not touch redis")
    void save_whenKeyIsNull_shouldNotTouchRedis() {
        // when
        tested.save(null, cachedResponse(), TTL);

        // then
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UT save() when ttl is null should not touch redis")
    void save_whenTtlIsNull_shouldNotTouchRedis() {
        // when
        tested.save(IDEMPOTENCY_KEY, cachedResponse(), null);

        // then
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UT save() when response is null should not touch redis")
    void save_whenResponseIsNull_shouldNotTouchRedis() {
        // when
        tested.save(IDEMPOTENCY_KEY, null, TTL);

        // then
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UT save() when ttl is zero should not touch redis")
    void save_whenTtlIsZero_shouldNotTouchRedis() {
        // when
        tested.save(IDEMPOTENCY_KEY, cachedResponse(), Duration.ZERO);

        // then
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UT save() when ttl is negative should not touch redis")
    void save_whenTtlIsNegative_shouldNotTouchRedis() {
        // when
        tested.save(IDEMPOTENCY_KEY, cachedResponse(), Duration.ofSeconds(-1));

        // then
        verifyNoInteractions(valueOperations);
    }

    private CachedResponse cachedResponse() {
        return new DefaultCachedResponse(
                201,
                "{\"id\":1}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                "fingerprint"
        );
    }
}
