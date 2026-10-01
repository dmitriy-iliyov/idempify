package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unlike the in-memory cache, this one never judges the expiry itself - redis does, through the TTL the entry
 * is written with, taken from the time the record has left. So a hit here means "redis answered with a key that
 * still has time", and the time handed back is the key's own. What the cases below hold down is which records
 * are worth writing at all, how long they are given, and what a hit reports as left of them.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheResponseRepositoryDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CACHE_NAME = "idempify";
    private static final String EXPECTED_KEY = "idempify:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private RedisTemplate<String, RawResponseContainer> redisTemplate;
    @Mock
    private ValueOperations<String, RawResponseContainer> valueOperations;

    private final CountingListener listener = new CountingListener();
    private ResponseRepository delegate;
    private RedisCacheResponseRepositoryDecorator tested;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        delegate = mock(ResponseRepository.class);
        tested = new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, CACHE_NAME, listener);
    }

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(null, redisTemplate, CACHE_NAME, listener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when redisTemplate is null should throw NullPointerException")
    void constructor_whenRedisTemplateIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, null, CACHE_NAME, listener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("redisTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when cacheName is null should throw NullPointerException")
    void constructor_whenCacheNameIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, null, listener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cacheName cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when listener is null should throw NullPointerException")
    void constructor_whenListenerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, CACHE_NAME, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener cannot be null");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key is null should answer empty without touching redis or the store")
    void findByIdempotencyKey_whenKeyIsNull_shouldAnswerEmptyWithoutTouchingRedisOrStore() {
        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(null);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(delegate);
        verify(redisTemplate, never()).executePipelined(any(SessionCallback.class));
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when redis answers should hand it back with the time its key has left, not the stored one")
    void findByIdempotencyKey_whenRedisAnswers_shouldHandItBackWithTimeItsKeyHasLeftNotStoredOne() {
        // given
        givenRedisHolds(answered(), 1_000L);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).hasValueSatisfying(container -> {
            assertThat(container.getResponse()).isEqualTo("raw-response");
            assertThat(container.getFingerprint()).isEqualTo("fingerprint");
            assertThat(container.getRemainingMillis()).isEqualTo(1_000L);
        });
        assertThat(listener.hits).isEqualTo(1);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key expired between the two reads should ask the store as on any miss")
    void findByIdempotencyKey_whenKeyExpiredBetweenTwoReads_shouldAskStoreAsOnAnyMiss() {
        // given
        givenRedisHolds(answered(), -2L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        assertThat(listener.hits).isZero();
        assertThat(listener.misses).isEqualTo(1);
        verify(delegate).findByIdempotencyKey(KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key has no ttl should not serve it for ever but ask the store")
    void findByIdempotencyKey_whenKeyHasNoTtl_shouldNotServeItForEverButAskStore() {
        // given
        givenRedisHolds(answered(), -1L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        assertThat(listener.misses).isEqualTo(1);
        verify(delegate).findByIdempotencyKey(KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when redis has nothing should ask the store and remember what it answered")
    void findByIdempotencyKey_whenRedisHasNothing_shouldAskStoreAndRememberWhatItAnswered() {
        // given
        RawResponseContainer stored = answered();
        givenRedisHolds(null, -2L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.of(stored));

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).containsSame(stored);
        assertThat(listener.misses).isEqualTo(1);
        verify(valueOperations).set(EXPECTED_KEY, stored, TTL);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the store has nothing either should remember nothing")
    void findByIdempotencyKey_whenStoreHasNothingEither_shouldRememberNothing() {
        // given
        givenRedisHolds(null, -2L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isEmpty();
        assertThat(listener.misses).isEqualTo(1);
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the operation has not answered yet should remember nothing")
    void findByIdempotencyKey_whenOperationHasNotAnsweredYet_shouldRememberNothing() {
        // given
        givenRedisHolds(null, -2L);
        when(delegate.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(new DefaultRawResponseContainer(null, "fingerprint", null)));

        // when
        tested.findByIdempotencyKey(KEY);

        // then
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the record has already expired should remember nothing")
    void findByIdempotencyKey_whenRecordHasAlreadyExpired_shouldRememberNothing() {
        // given
        givenRedisHolds(null, -2L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.of(
                new DefaultRawResponseContainer("raw-response", "fingerprint", -1_000L)));

        // when
        tested.findByIdempotencyKey(KEY);

        // then
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the record has exactly no time left should remember nothing")
    void findByIdempotencyKey_whenRecordHasExactlyNoTimeLeft_shouldRememberNothing() {
        // given
        givenRedisHolds(null, -2L);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.of(
                new DefaultRawResponseContainer("raw-response", "fingerprint", 0L)));

        // when
        tested.findByIdempotencyKey(KEY);

        // then
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("UT save() should write through to the store and remember what it wrote for what is left of its life")
    void save_shouldWriteThroughToStoreAndRememberWhatItWroteForWhatIsLeftOfItsLife() {
        // given
        RawResponseContainer saved = answered();
        when(delegate.save(KEY, "raw-response")).thenReturn(saved);

        // when
        RawResponseContainer result = tested.save(KEY, "raw-response");

        // then
        assertThat(result).isSameAs(saved);
        verify(valueOperations).set(EXPECTED_KEY, saved, TTL);
    }

    @Test
    @DisplayName("UT save() when the stored record carries no expiry should write through and remember nothing")
    void save_whenStoredRecordCarriesNoExpiry_shouldWriteThroughAndRememberNothing() {
        // given
        when(delegate.save(KEY, "raw-response"))
                .thenReturn(new DefaultRawResponseContainer("raw-response", "fingerprint", null));

        // when
        tested.save(KEY, "raw-response");

        // then
        verify(delegate).save(KEY, "raw-response");
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("UT save() when the key is null should write through without remembering anything")
    void save_whenKeyIsNull_shouldWriteThroughWithoutRememberingAnything() {
        // when
        tested.save(null, "raw-response");

        // then
        verify(delegate).save(null, "raw-response");
        verify(valueOperations, never()).set(any(), any(), any());
    }

    private void givenRedisHolds(RawResponseContainer container, long remainingMillis) {
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(Arrays.asList(container, remainingMillis));
    }

    private static RawResponseContainer answered() {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", TTL.toMillis());
    }

    private static final class CountingListener implements CacheEventListener {

        private int hits;
        private int misses;

        @Override
        public void onHit() {
            hits++;
        }

        @Override
        public void onMiss() {
            misses++;
        }
    }
}
