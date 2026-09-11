package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order the wrappers are applied in is the whole point of this helper, so the tests read it off the calls
 * a lookup actually makes: the wrapper that runs first is the one furthest from the repository.
 */
class ResponseRepositoryWrapperUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT wrapWithPriority() when repository is null should throw NullPointerException")
    void wrapWithPriority_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> ResponseRepositoryWrapperUtils.wrapWithPriority(null, Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("repository cannot be null");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when wrappers are null should throw NullPointerException")
    void wrapWithPriority_whenWrappersAreNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() ->
                ResponseRepositoryWrapperUtils.wrapWithPriority(new RecordingRepository(new ArrayList<>()), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("wrappers cannot be null");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when nobody wraps should hand back the repository itself")
    void wrapWithPriority_whenNobodyWraps_shouldHandBackRepositoryItself() {
        // given
        ResponseRepository repository = new RecordingRepository(new ArrayList<>());

        // when
        ResponseRepository result = ResponseRepositoryWrapperUtils.wrapWithPriority(repository, Set.of());

        // then
        assertThat(result).isSameAs(repository);
    }

    @Test
    @DisplayName("UT wrapWithPriority() when one wrapper is given should let it see every call")
    void wrapWithPriority_whenOneWrapperIsGiven_shouldLetItSeeEveryCall() {
        // given
        List<String> calls = new ArrayList<>();
        ResponseRepository repository = new RecordingRepository(calls);

        // when
        ResponseRepository result =
                ResponseRepositoryWrapperUtils.wrapWithPriority(repository, Set.of(wrapper("only", 0, calls)));
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("only.find", "repository.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() should put the highest priority closest to the repository")
    void wrapWithPriority_shouldPutHighestPriorityClosestToRepository() {
        // given
        List<String> calls = new ArrayList<>();
        Set<ResponseRepositoryWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("low", 1, calls),
                wrapper("high", 100, calls),
                wrapper("middle", 50, calls)
        ));

        // when
        ResponseRepository result =
                ResponseRepositoryWrapperUtils.wrapWithPriority(new RecordingRepository(calls), wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("low.find", "middle.find", "high.find", "repository.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() should order by priority whatever order the wrappers arrive in")
    void wrapWithPriority_shouldOrderByPriorityWhateverOrderWrappersArriveIn() {
        // given
        List<String> calls = new ArrayList<>();
        Set<ResponseRepositoryWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("high", 100, calls),
                wrapper("low", 1, calls)
        ));

        // when
        ResponseRepository result =
                ResponseRepositoryWrapperUtils.wrapWithPriority(new RecordingRepository(calls), wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("low.find", "high.find", "repository.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() when a wrapper carries a negative priority should still keep it outermost")
    void wrapWithPriority_whenWrapperCarriesNegativePriority_shouldStillKeepItOutermost() {
        // given
        List<String> calls = new ArrayList<>();
        Set<ResponseRepositoryWrapper> wrappers = new LinkedHashSet<>(List.of(
                wrapper("default", 0, calls),
                wrapper("negative", Integer.MIN_VALUE, calls)
        ));

        // when
        ResponseRepository result =
                ResponseRepositoryWrapperUtils.wrapWithPriority(new RecordingRepository(calls), wrappers);
        result.findByIdempotencyKey(KEY);

        // then
        assertThat(calls).containsExactly("negative.find", "default.find", "repository.find");
    }

    @Test
    @DisplayName("UT wrapWithPriority() should let a wrapper see a save as well as a lookup")
    void wrapWithPriority_shouldLetWrapperSeeSaveAsWellAsLookup() {
        // given
        List<String> calls = new ArrayList<>();

        // when
        ResponseRepository result = ResponseRepositoryWrapperUtils.wrapWithPriority(
                new RecordingRepository(calls),
                Set.of(wrapper("only", 0, calls))
        );
        result.save(KEY, "raw-response");

        // then
        assertThat(calls).containsExactly("only.save", "repository.save");
    }

    private static ResponseRepositoryWrapper wrapper(String name, int priority, List<String> calls) {
        return new ResponseRepositoryWrapper() {

            @Override
            public ResponseRepository wrap(ResponseRepository repository) {
                return new NamingDecorator(repository, name, calls);
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }

    /**
     * Writes down that it was reached and under which name, so the assertion reads the nesting order off the
     * list instead of unwrapping the decorators.
     */
    private static final class NamingDecorator extends AbstractResponseRepositoryDecorator {

        private final String name;
        private final List<String> calls;

        private NamingDecorator(ResponseRepository delegate, String name, List<String> calls) {
            super(delegate);
            this.name = name;
            this.calls = calls;
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            calls.add(name + ".save");
            return super.save(idempotencyKey, response);
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            calls.add(name + ".find");
            return super.findByIdempotencyKey(idempotencyKey);
        }
    }

    private static final class RecordingRepository implements ResponseRepository {

        private final List<String> calls;

        private RecordingRepository(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            calls.add("repository.save");
            return new DefaultRawResponseContainer(response, "fingerprint", null);
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            calls.add("repository.find");
            return Optional.empty();
        }
    }
}
