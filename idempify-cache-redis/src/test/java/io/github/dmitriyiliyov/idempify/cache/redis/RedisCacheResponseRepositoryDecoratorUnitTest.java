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
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unlike the in-memory cache, this one never judges the expiry itself - redis does, through the TTL the entry
 * is written with. So a hit here means "redis answered", and what the cases below hold down is which records
 * are worth writing at all and how long they are given.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheResponseRepositoryDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String CACHE_NAME = "idempify";
    private static final String EXPECTED_KEY = "idempify:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private RedisTemplate<String, RawResponseContainer> redisTemplate;
    @Mock
    private ValueOperations<String, RawResponseContainer> valueOperations;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CountingListener listener = new CountingListener();
    private ResponseRepository delegate;
    private RedisCacheResponseRepositoryDecorator tested;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        delegate = mock(ResponseRepository.class);
        tested = new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, CACHE_NAME, listener, clock);
    }

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(null, redisTemplate, CACHE_NAME, listener, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when redisTemplate is null should throw NullPointerException")
    void constructor_whenRedisTemplateIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, null, CACHE_NAME, listener, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("redisTemplate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when cacheName is null should throw NullPointerException")
    void constructor_whenCacheNameIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, null, listener, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cacheName cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when listener is null should throw NullPointerException")
    void constructor_whenListenerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, CACHE_NAME, null, clock))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                new RedisCacheResponseRepositoryDecorator(delegate, redisTemplate, CACHE_NAME, listener, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key is null should answer empty without touching redis or the store")
    void findByIdempotencyKey_whenKeyIsNull_shouldAnswerEmptyWithoutTouchingRedisOrStore() {
        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(null);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(delegate);
        verify(valueOperations, never()).get(EXPECTED_KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when redis answers should hand that back without asking the store")
    void findByIdempotencyKey_whenRedisAnswers_shouldHandThatBackWithoutAskingStore() {
        // given
        RawResponseContainer cached = answered();
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(cached);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).containsSame(cached);
        assertThat(listener.hits).isEqualTo(1);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when redis has nothing should ask the store and remember what it answered")
    void findByIdempotencyKey_whenRedisHasNothing_shouldAskStoreAndRememberWhatItAnswered() {
        // given
        RawResponseContainer stored = answered();
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
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
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
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
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
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
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        when(delegate.findByIdempotencyKey(KEY)).thenReturn(Optional.of(
                new DefaultRawResponseContainer("raw-response", "fingerprint", NOW.minusSeconds(1))));

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

    private static RawResponseContainer answered() {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", NOW.plus(TTL));
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
