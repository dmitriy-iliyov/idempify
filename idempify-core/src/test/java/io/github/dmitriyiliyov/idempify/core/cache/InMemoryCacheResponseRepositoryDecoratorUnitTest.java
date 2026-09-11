package io.github.dmitriyiliyov.idempify.core.cache;

import io.github.dmitriyiliyov.idempify.core.TestClock;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cache sits in front of the repository, so every test here judges two things at once: what the caller
 * got back, and whether the repository was reached at all. What may be remembered is decided by the record
 * itself - a row still waiting for its answer, or one without an expiry, has nothing cacheable on it.
 */
class InMemoryCacheResponseRepositoryDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID THIRD_KEY = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = TestClock.EPOCH;
    private static final Duration TTL = Duration.ofMinutes(5);

    private final TestClock clock = TestClock.fixedAt(NOW);
    private final CountingListener listener = new CountingListener();

    @Test
    @DisplayName("UT constructor() when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryCacheResponseRepositoryDecorator(null, 10, clock, listener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryCacheResponseRepositoryDecorator(repository(), 10, null, listener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when listener is null should throw NullPointerException")
    void constructor_whenListenerIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryCacheResponseRepositoryDecorator(repository(), 10, clock, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("listener cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when capacity is zero should throw IllegalArgumentException")
    void constructor_whenCapacityIsZero_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryCacheResponseRepositoryDecorator(repository(), 0, clock, listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capacity cannot be ZERO or negative");
    }

    @Test
    @DisplayName("UT constructor() when capacity is negative should throw IllegalArgumentException")
    void constructor_whenCapacityIsNegative_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new InMemoryCacheResponseRepositoryDecorator(repository(), -1, clock, listener))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capacity cannot be ZERO or negative");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the key is null should answer empty without reaching the repository")
    void findByIdempotencyKey_whenKeyIsNull_shouldAnswerEmptyWithoutReachingRepository() {
        // given
        RecordingRepository repository = repository();
        ResponseRepository tested = cache(repository, 10);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(null);

        // then
        assertThat(result).isEmpty();
        assertThat(repository.lookups).isEmpty();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when nothing is cached should ask the repository and report a miss")
    void findByIdempotencyKey_whenNothingIsCached_shouldAskRepositoryAndReportMiss() {
        // given
        RawResponseContainer stored = answered();
        RecordingRepository repository = repository(Map.of(KEY, stored));
        ResponseRepository tested = cache(repository, 10);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).containsSame(stored);
        assertThat(repository.lookups).containsExactly(KEY);
        assertThat(listener.misses).isEqualTo(1);
        assertThat(listener.hits).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the repository has nothing should not remember the miss")
    void findByIdempotencyKey_whenRepositoryHasNothing_shouldNotRememberMiss() {
        // given
        RecordingRepository repository = repository();
        ResponseRepository tested = cache(repository, 10);

        // when
        tested.findByIdempotencyKey(KEY);
        tested.findByIdempotencyKey(KEY);

        // then
        assertThat(repository.lookups).containsExactly(KEY, KEY);
        assertThat(listener.misses).isEqualTo(2);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the entry is cached should answer without reaching the repository")
    void findByIdempotencyKey_whenEntryIsCached_shouldAnswerWithoutReachingRepository() {
        // given
        RawResponseContainer stored = answered();
        RecordingRepository repository = repository(Map.of(KEY, stored));
        ResponseRepository tested = cache(repository, 10);
        tested.findByIdempotencyKey(KEY);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).containsSame(stored);
        assertThat(repository.lookups).containsExactly(KEY);
        assertThat(listener.hits).isEqualTo(1);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when capacity is exceeded should forget the least recently added key")
    void findByIdempotencyKey_whenCapacityIsExceeded_shouldForgetLeastRecentlyAddedKey() {
        // given
        RecordingRepository repository = repository(Map.of(
                KEY, answered(),
                OTHER_KEY, answered(),
                THIRD_KEY, answered()
        ));
        ResponseRepository tested = cache(repository, 2);
        tested.findByIdempotencyKey(KEY);
        tested.findByIdempotencyKey(OTHER_KEY);
        tested.findByIdempotencyKey(THIRD_KEY);

        // when
        tested.findByIdempotencyKey(KEY);

        // then
        assertThat(repository.lookups).containsExactly(KEY, OTHER_KEY, THIRD_KEY, KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the operation has not answered yet should not remember it")
    void findByIdempotencyKey_whenOperationHasNotAnsweredYet_shouldNotRememberIt() {
        // given
        RecordingRepository repository = repository(Map.of(KEY, claimed()));
        ResponseRepository tested = cache(repository, 10);

        // when
        tested.findByIdempotencyKey(KEY);
        tested.findByIdempotencyKey(KEY);

        // then
        assertThat(repository.lookups).containsExactly(KEY, KEY);
        assertThat(listener.hits).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the record carries no expiry should not remember it")
    void findByIdempotencyKey_whenRecordCarriesNoExpiry_shouldNotRememberIt() {
        // given
        RecordingRepository repository = repository(Map.of(
                KEY, new DefaultRawResponseContainer("raw-response", "fingerprint", null)));
        ResponseRepository tested = cache(repository, 10);

        // when
        tested.findByIdempotencyKey(KEY);
        tested.findByIdempotencyKey(KEY);

        // then
        assertThat(repository.lookups).containsExactly(KEY, KEY);
        assertThat(listener.hits).isZero();
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the remembered record has expired should ask the repository again")
    void findByIdempotencyKey_whenRememberedRecordHasExpired_shouldAskRepositoryAgain() {
        // given
        RecordingRepository repository = repository(Map.of(KEY, answered()));
        ResponseRepository tested = cache(repository, 10);
        tested.findByIdempotencyKey(KEY);

        // when
        clock.advance(TTL);
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).isPresent();
        assertThat(repository.lookups).containsExactly(KEY, KEY);
        assertThat(listener.misses).isEqualTo(2);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the remembered record has expired and the row is gone should answer empty")
    void findByIdempotencyKey_whenRememberedRecordHasExpiredAndRowIsGone_shouldAnswerEmpty() {
        // given
        RecordingRepository repository = repository(Map.of(KEY, answered()));
        ResponseRepository tested = cache(repository, 10);
        tested.findByIdempotencyKey(KEY);
        repository.forget();

        // when
        clock.advance(TTL);

        // then
        assertThat(tested.findByIdempotencyKey(KEY)).isEmpty();
    }

    @Test
    @DisplayName("UT save() should write through to the repository and hand back what it wrote")
    void save_shouldWriteThroughToRepositoryAndHandBackWhatItWrote() {
        // given
        RecordingRepository repository = repository();
        ResponseRepository tested = cache(repository, 10);

        // when
        RawResponseContainer result = tested.save(KEY, "raw-response");

        // then
        assertThat(result.getResponse()).isEqualTo("raw-response");
        assertThat(repository.saves).containsExactly(KEY);
    }

    @Test
    @DisplayName("UT save() should remember what it wrote so the next lookup never reaches the repository")
    void save_shouldRememberWhatItWroteSoNextLookupNeverReachesRepository() {
        // given
        RecordingRepository repository = repository();
        ResponseRepository tested = cache(repository, 10);

        // when
        tested.save(KEY, "raw-response");
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).hasValueSatisfying(container ->
                assertThat(container.getResponse()).isEqualTo("raw-response"));
        assertThat(repository.lookups).isEmpty();
        assertThat(listener.hits).isEqualTo(1);
    }

    @Test
    @DisplayName("UT save() when the key is null should still write through without remembering anything")
    void save_whenKeyIsNull_shouldStillWriteThroughWithoutRememberingAnything() {
        // given
        RecordingRepository repository = repository();
        ResponseRepository tested = cache(repository, 10);

        // when
        tested.save(null, "raw-response");

        // then
        assertThat(repository.saves).containsExactly((UUID) null);
    }

    private ResponseRepository cache(ResponseRepository repository, int capacity) {
        return new InMemoryCacheResponseRepositoryDecorator(repository, capacity, clock, listener);
    }

    private static RecordingRepository repository() {
        return new RecordingRepository(new HashMap<>());
    }

    private static RecordingRepository repository(Map<UUID, RawResponseContainer> byKey) {
        return new RecordingRepository(new HashMap<>(byKey));
    }

    private static RawResponseContainer answered() {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", NOW.plus(TTL));
    }

    private static RawResponseContainer claimed() {
        return new DefaultRawResponseContainer(null, "fingerprint", null);
    }

    /**
     * Writes down every key it was asked for and every key written through it, so a test can assert that a
     * hit never reached it.
     */
    private static final class RecordingRepository implements ResponseRepository {

        private final Map<UUID, RawResponseContainer> byKey;
        private final List<UUID> lookups = new ArrayList<>();
        private final List<UUID> saves = new ArrayList<>();

        private RecordingRepository(Map<UUID, RawResponseContainer> byKey) {
            this.byKey = byKey;
        }

        private void forget() {
            byKey.clear();
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            saves.add(idempotencyKey);
            RawResponseContainer saved =
                    new DefaultRawResponseContainer(response, "fingerprint", NOW.plus(TTL));
            byKey.put(idempotencyKey, saved);
            return saved;
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            lookups.add(idempotencyKey);
            return Optional.ofNullable(byKey.get(idempotencyKey));
        }
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
