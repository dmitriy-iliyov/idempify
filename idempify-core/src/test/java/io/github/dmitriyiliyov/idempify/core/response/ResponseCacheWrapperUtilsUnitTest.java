package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order the wrappers are applied in is the whole point of this helper, so the tests read it off the calls
 * a request actually makes: the wrapper that runs first is the one furthest from the cache.
 */
class ResponseCacheWrapperUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT wrapWithPriority() when cache is null should throw NullPointerException")
    void wrapWithPriority_whenCacheIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseCacheWrapperUtils.wrapWithPriority(null, Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("cache cannot be null");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when wrappers are null should throw NullPointerException")
    void wrapWithPriority_whenWrappersAreNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseCacheWrapperUtils.wrapWithPriority(new RecordingCache(new ArrayList<>()), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("wrappers cannot be null");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when nobody wraps should hand back the cache itself")
    void wrapWithPriority_whenNobodyWraps_shouldHandBackCacheItself() {
        // given
        ResponseCache cache = new RecordingCache(new ArrayList<>());

        // when
        ResponseCache result = ResponseCacheWrapperUtils.wrapWithPriority(cache, Set.of());

        // then
        assertThat(result).isSameAs(cache);
    }

    @Test
    @DisplayName("UT wrapWithPriority() when one wrapper is given should let it see every call")
    void wrapWithPriority_whenOneWrapperIsGiven_shouldLetItSeeEveryCall() {
        // given
        List<String> calls = new ArrayList<>();
        ResponseCache cache = new RecordingCache(calls);

        // when
        ResponseCache result = ResponseCacheWrapperUtils.wrapWithPriority(cache, Set.of(wrapper("only", 0, calls)));
        result.findByIdempotencyKey(KEY);
        result.save(KEY, response(), Duration.ofMinutes(1));

        // then
        assertThat(calls).containsExactly("only.find", "cache.find", "only.save", "cache.save");
    }

    @Test
    @DisplayName("UT wrapWithPriority() should put the highest priority closest to the cache")
    void wrapWithPriority_shouldPutHighestPriorityClosestToCache() {
        // given
        List<String> calls = new ArrayList<>();
        ResponseCache cache = new RecordingCache(calls);
        Set<ResponseCacheWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("low", 1, calls),
                wrapper("high", 100, calls),
                wrapper("middle", 50, calls)
        ));

        // when
        ResponseCache result = ResponseCacheWrapperUtils.wrapWithPriority(cache, wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("low.find", "middle.find", "high.find", "cache.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() should order by priority whatever order the wrappers arrive in")
    void wrapWithPriority_shouldOrderByPriorityWhateverOrderWrappersArriveIn() {
        // given
        List<String> calls = new ArrayList<>();
        Set<ResponseCacheWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("high", 100, calls),
                wrapper("low", 1, calls)
        ));

        // when
        ResponseCache result = ResponseCacheWrapperUtils.wrapWithPriority(new RecordingCache(calls), wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("low.find", "high.find", "cache.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when a wrapper carries a negative priority should still keep it outermost")
    void wrapWithPriority_whenWrapperCarriesNegativePriority_shouldStillKeepItOutermost() {
        // given
        List<String> calls = new ArrayList<>();
        Set<ResponseCacheWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("default", 0, calls),
                wrapper("negative", Integer.MIN_VALUE, calls)
        ));

        // when
        ResponseCache result = ResponseCacheWrapperUtils.wrapWithPriority(new RecordingCache(calls), wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("negative.find", "default.find", "cache.find");
    }

    private static ResponseCacheWrapper wrapper(String name, int priority, List<String> calls) {
        return new ResponseCacheWrapper() {

            @Override
            public ResponseCache wrap(ResponseCache responseCache) {
                return new NamingDecorator(responseCache, name, calls);
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    private static CachedResponse response() {
        return new DefaultCachedResponse(200, new byte[]{1}, "application/json", null);
    }

    /**
     * Writes down that it was reached and under which name, so the assertion reads the nesting order off the
     * list instead of unwrapping the decorators.
     */
    private static final class NamingDecorator extends AbstractResponseCacheDecorator {

        private final String name;
        private final List<String> calls;

        private NamingDecorator(ResponseCache delegate, String name, List<String> calls) {
            super(delegate);
            this.name = name;
            this.calls = calls;
        }

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            calls.add(name + ".find");
            return super.findByIdempotencyKey(idempotencyKey);
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
            calls.add(name + ".save");
            super.save(idempotencyKey, response, ttl);
        }
    }

    private static final class RecordingCache implements ResponseCache {

        private final List<String> calls;

        private RecordingCache(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
            calls.add("cache.find");
            return null;
        }

        @Override
        public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) {
            calls.add("cache.save");
        }
    }
}
