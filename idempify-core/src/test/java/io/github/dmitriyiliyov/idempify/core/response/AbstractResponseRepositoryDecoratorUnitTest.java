package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A decorator that adds nothing must be indistinguishable from the repository it wraps: whatever a subclass
 * does not override has to reach the delegate unchanged and come back unchanged.
 */
class AbstractResponseRepositoryDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT constructor() when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new PassThroughDecorator(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT save() should hand the response to the delegate and return what it wrote")
    void save_shouldHandResponseToDelegateAndReturnWhatItWrote() {
        // given
        RecordingRepository delegate = new RecordingRepository(Optional.of(container()));
        ResponseRepository tested = new PassThroughDecorator(delegate);

        // when
        RawResponseContainer result = tested.save(KEY, "raw-response");

        // then
        assertThat(result.getResponse()).isEqualTo("raw-response");
        assertThat(delegate.lastSavedKey).isEqualTo(KEY);
        assertThat(delegate.lastSavedResponse).isEqualTo("raw-response");
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() should ask the delegate and hand back what it answered")
    void findByIdempotencyKey_shouldAskDelegateAndHandBackWhatItAnswered() {
        // given
        RawResponseContainer stored = container();
        RecordingRepository delegate = new RecordingRepository(Optional.of(stored));
        ResponseRepository tested = new PassThroughDecorator(delegate);

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(KEY);

        // then
        assertThat(result).containsSame(stored);
        assertThat(delegate.lastFoundKey).isEqualTo(KEY);
    }

    @Test
    @DisplayName("UT findByIdempotencyKey() when the delegate has nothing should report the miss unchanged")
    void findByIdempotencyKey_whenDelegateHasNothing_shouldReportMissUnchanged() {
        // given
        ResponseRepository tested = new PassThroughDecorator(new RecordingRepository(Optional.empty()));

        // when / then
        assertThat(tested.findByIdempotencyKey(KEY)).isEmpty();
    }

    private static RawResponseContainer container() {
        return new DefaultRawResponseContainer(
                "raw-response",
                "fingerprint",
                Instant.parse("2026-08-10T12:00:00Z")
        );
    }

    private static final class PassThroughDecorator extends AbstractResponseRepositoryDecorator {

        private PassThroughDecorator(ResponseRepository delegate) {
            super(delegate);
        }
    }

    private static final class RecordingRepository implements ResponseRepository {

        private final Optional<RawResponseContainer> answer;
        private UUID lastFoundKey;
        private UUID lastSavedKey;
        private String lastSavedResponse;

        private RecordingRepository(Optional<RawResponseContainer> answer) {
            this.answer = answer;
        }

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            lastSavedKey = idempotencyKey;
            lastSavedResponse = response;
            return new DefaultRawResponseContainer(response, "fingerprint", null);
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            lastFoundKey = idempotencyKey;
            return answer;
        }
    }
}
