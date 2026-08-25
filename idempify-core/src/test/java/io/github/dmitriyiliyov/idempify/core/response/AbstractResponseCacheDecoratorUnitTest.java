package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A decorator that adds nothing must be indistinguishable from the cache it wraps: whatever a subclass does
 * not override has to reach the delegate unchanged and come back unchanged.
 */
class AbstractResponseCacheDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT constructor when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new PassThroughDecorator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delegate cannot be null");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() should ask the delegate and hand back what it answered")
    void findByIdempotencyKey_shouldAskDelegateAndHandBackWhatItAnswered() {
        // given
        CachedResponse stored = new DefaultCachedResponse(201, new byte[]{1}, "application/json", null);
        RecordingCache delegate = new RecordingCache(stored);
        ResponseCache tested = new PassThroughDecorator(delegate);

        // when
        CachedResponse result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isSameAs(stored);
        assertThat(delegate.lastFoundKey).isEqualTo(KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the delegate has nothing should report the miss unchanged")
    void findByIdempotencyKey_whenDelegateHasNothing_shouldReportMissUnchanged() {
        // given
        ResponseCache tested = new PassThroughDecorator(new RecordingCache(null));

        // when / then
        assertThat(tested.findByIdempotencyKey(KEY)).isNull();
    }

    @Test
    @DisplayName("UT save() should pass the key, the response and the ttl to the delegate untouched")
    void save_shouldPassKeyResponseAndTtlToDelegateUntouched() {
        // given
        RecordingCache delegate = new RecordingCache(null);
        ResponseCache tested = new PassThroughDecorator(delegate);
        CachedResponse response = new DefaultCachedResponse(200, new byte[]{7}, "text/plain", "fp");

        // when
        tested.save(KEY, response, Duration.ofMinutes(5));

        // then
        assertThat(delegate.lastSavedKey).isEqualTo(KEY);
        assertThat(delegate.lastSavedResponse).isSameAs(response);
        assertThat(delegate.lastSavedTtl).isEqualTo(Duration.ofMinutes(5));
    }

    private static final class PassThroughDecorator extends AbstractResponseCacheDecorator {

        private PassThroughDecorator(ResponseCache delegate) {
            super(delegate);
        }
    }

    private static final class RecordingCache implements ResponseCache {

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
}
