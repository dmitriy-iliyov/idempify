package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryResponseCacheUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID THIRD_KEY = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(NOW);

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryResponseCache(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT constructor when capacity is zero should throw IllegalArgumentException")
    void constructor_whenCapacityIsZero_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryResponseCache(0, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity cannot be ZERO or negative");
    }

    @Test
    @DisplayName("UT constructor when capacity is negative should throw IllegalArgumentException")
    void constructor_whenCapacityIsNegative_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryResponseCache(-1, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity cannot be ZERO or negative");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key was never saved should return null")
    void findByIdempotencyKey_whenKeyWasNeverSaved_shouldReturnNull() {
        // given
        InMemoryResponseCache tested = cache(10);

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key is null should return null")
    void findByIdempotencyKey_whenKeyIsNull_shouldReturnNull() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), TTL);

        // when
        CachedResponse result = tested.findByIdempotencyKey(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the entry is still within its ttl should return the stored response")
    void findByIdempotencyKey_whenEntryIsStillWithinItsTtl_shouldReturnStoredResponse() {
        // given
        InMemoryResponseCache tested = cache(10);
        CachedResponse stored = response("paid");
        tested.save(KEY, stored, TTL);
        clock.advance(TTL.minusSeconds(1));

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isSameAs(stored);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the ttl has exactly run out should return null")
    void findByIdempotencyKey_whenTtlHasExactlyRunOut_shouldReturnNull() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), TTL);
        clock.advance(TTL);

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the entry is expired should forget it instead of only hiding it")
    void findByIdempotencyKey_whenEntryIsExpired_shouldForgetItInsteadOfOnlyHidingIt() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), TTL);
        clock.advance(TTL);
        tested.findByIdempotencyKey(KEY);

        // when
        clock.rewind(TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when another key expired should leave the fresh one alone")
    void findByIdempotencyKey_whenAnotherKeyExpired_shouldLeaveFreshOneAlone() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), Duration.ofMinutes(1));
        tested.save(OTHER_KEY, response("refunded"), Duration.ofHours(1));
        clock.advance(Duration.ofMinutes(2));

        // when
        CachedResponse expired = tested.findByIdempotencyKey(KEY);
        CachedResponse fresh = tested.findByIdempotencyKey(OTHER_KEY);

        // then
        assertThat(expired).isNull();
        assertThat(fresh).isEqualTo(response("refunded"));
    }

    @Test
    @DisplayName("UT save() when the key is saved twice should return the latest response")
    void save_whenKeyIsSavedTwice_shouldReturnLatestResponse() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), TTL);

        // when
        tested.save(KEY, response("refunded"), TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isEqualTo(response("refunded"));
    }

    @Test
    @DisplayName("UT save() when the key is saved again should restart its ttl from the new write")
    void save_whenKeyIsSavedAgain_shouldRestartItsTtlFromNewWrite() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), TTL);
        clock.advance(TTL.minusSeconds(1));

        // when
        tested.save(KEY, response("paid"), TTL);
        clock.advance(TTL.minusSeconds(1));

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isEqualTo(response("paid"));
    }

    @Test
    @DisplayName("UT save() when the ttl is zero should store nothing")
    void save_whenTtlIsZero_shouldStoreNothing() {
        // given
        InMemoryResponseCache tested = cache(10);

        // when
        tested.save(KEY, response("paid"), Duration.ZERO);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT save() when the ttl is negative should store nothing")
    void save_whenTtlIsNegative_shouldStoreNothing() {
        // given
        InMemoryResponseCache tested = cache(10);

        // when
        tested.save(KEY, response("paid"), Duration.ofSeconds(-1));

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT save() when the ttl is shorter than a second should still be honoured")
    void save_whenTtlIsShorterThanSecond_shouldStillBeHonoured() {
        // given
        InMemoryResponseCache tested = cache(10);
        tested.save(KEY, response("paid"), Duration.ofMillis(800));

        // when
        CachedResponse beforeExpiry = tested.findByIdempotencyKey(KEY);
        clock.advance(Duration.ofMillis(800));
        CachedResponse afterExpiry = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(beforeExpiry).isEqualTo(response("paid"));
        assertThat(afterExpiry).isNull();
    }

    @Test
    @DisplayName("UT save() when the ttl is null should store nothing")
    void save_whenTtlIsNull_shouldStoreNothing() {
        // given
        InMemoryResponseCache tested = cache(10);

        // when
        tested.save(KEY, response("paid"), null);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT save() when the response is null should store nothing")
    void save_whenResponseIsNull_shouldStoreNothing() {
        // given
        InMemoryResponseCache tested = cache(10);

        // when
        tested.save(KEY, null, TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT save() when the key is null should store nothing")
    void save_whenKeyIsNull_shouldStoreNothing() {
        // given
        InMemoryResponseCache tested = cache(1);
        tested.save(KEY, response("paid"), TTL);

        // when
        tested.save(null, response("refunded"), TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isEqualTo(response("paid"));
    }

    @Test
    @DisplayName("UT save() when the capacity is reached should give up the oldest write")
    void save_whenCapacityIsReached_shouldGiveUpOldestWrite() {
        // given
        InMemoryResponseCache tested = cache(2);
        tested.save(KEY, response("first"), TTL);
        tested.save(OTHER_KEY, response("second"), TTL);

        // when
        tested.save(THIRD_KEY, response("third"), TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
        assertThat(tested.findByIdempotencyKey(OTHER_KEY)).isEqualTo(response("second"));
        assertThat(tested.findByIdempotencyKey(THIRD_KEY)).isEqualTo(response("third"));
    }

    @Test
    @DisplayName("UT save() when an existing key is rewritten should not count against the capacity twice")
    void save_whenExistingKeyIsRewritten_shouldNotCountAgainstCapacityTwice() {
        // given
        InMemoryResponseCache tested = cache(2);
        tested.save(KEY, response("first"), TTL);
        tested.save(OTHER_KEY, response("second"), TTL);

        // when
        tested.save(KEY, response("first again"), TTL);

        // then
        assertThat(tested.findByIdempotencyKey(OTHER_KEY)).isEqualTo(response("second"));
        assertThat(tested.findByIdempotencyKey(KEY)).isEqualTo(response("first again"));
    }

    @Test
    @DisplayName("UT save() when an existing key is rewritten should move it behind the entries written before it")
    void save_whenExistingKeyIsRewritten_shouldMoveItBehindEntriesWrittenBeforeIt() {
        // given
        InMemoryResponseCache tested = cache(2);
        tested.save(KEY, response("first"), TTL);
        tested.save(OTHER_KEY, response("second"), TTL);
        tested.save(KEY, response("first again"), TTL);

        // when
        tested.save(THIRD_KEY, response("third"), TTL);

        // then
        assertThat(tested.findByIdempotencyKey(OTHER_KEY)).isNull();
        assertThat(tested.findByIdempotencyKey(KEY)).isEqualTo(response("first again"));
        assertThat(tested.findByIdempotencyKey(THIRD_KEY)).isEqualTo(response("third"));
    }

    private InMemoryResponseCache cache(int capacity) {
        return new InMemoryResponseCache(capacity, clock);
    }

    private static CachedResponse response(String body) {
        return new DefaultCachedResponse(201, body.getBytes(StandardCharsets.UTF_8), "application/json", null);
    }

    /**
     * Lets a test move time the way a running system would, without any dependency on the wall clock.
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        void rewind(Duration by) {
            instant = instant.minus(by);
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
