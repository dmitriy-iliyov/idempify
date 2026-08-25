package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The behaviour every built-in policy inherits: how a stored fingerprint is compared against a fresh one, and
 * what happens when they disagree.
 */
class AbstractFingerprintPolicyUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final TestPolicy tested = new TestPolicy(new ThrowingEmptyBodyFallback());

    @Test
    @DisplayName("UT constructor when fallback is null should throw NullPointerException")
    void constructor_whenFallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new TestPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fallback cannot be null");
    }

    @Test
    @DisplayName("UT match() when the two fingerprints are the same should report the same request")
    void match_whenTwoFingerprintsAreSame_shouldReportSameRequest() {
        assertThat(tested.match("abc", "abc")).isTrue();
    }

    @Test
    @DisplayName("UT match() when the fingerprints differ should report a different request")
    void match_whenFingerprintsDiffer_shouldReportDifferentRequest() {
        assertThat(tested.match("abc", "abd")).isFalse();
    }

    @Test
    @DisplayName("UT match() when nothing was stored to compare against should not report a match")
    void match_whenNothingWasStoredToCompareAgainst_shouldNotReportMatch() {
        assertThat(tested.match(null, "abc")).isFalse();
    }

    @Test
    @DisplayName("UT match() when the current request has no fingerprint should not report a match")
    void match_whenCurrentRequestHasNoFingerprint_shouldNotReportMatch() {
        assertThat(tested.match("abc", null)).isFalse();
    }

    @Test
    @DisplayName("UT match() when neither side has a fingerprint should not report a match")
    void match_whenNeitherSideHasFingerprint_shouldNotReportMatch() {
        assertThat(tested.match(null, null)).isFalse();
    }

    @Test
    @DisplayName("UT handle() should refuse the request rather than let it be answered from another one")
    void handle_shouldRefuseRequestRatherThanLetItBeAnsweredFromAnotherOne() {
        // given
        FingerprintMismatchContext context = new DefaultFingerprintMismatchContext(KEY, "stored", "current");

        // when / then
        assertThatThrownBy(() -> tested.handle(context))
                .isInstanceOf(FingerprintMismatchException.class);
    }

    @Test
    @DisplayName("UT handle() should say which key mismatched so the failure can be read from a log alone")
    void handle_shouldSayWhichKeyMismatchedSoFailureCanBeReadFromLogAlone() {
        // given
        FingerprintMismatchContext context = new DefaultFingerprintMismatchContext(KEY, "stored", "current");

        // when / then
        assertThatThrownBy(() -> tested.handle(context))
                .hasMessageContaining(KEY.toString());
    }

    @Test
    @DisplayName("UT handle() should carry the context so a custom policy can read both fingerprints off it")
    void handle_shouldCarryContextSoCustomPolicyCanReadBothFingerprintsOffIt() {
        // given
        FingerprintMismatchContext context = new DefaultFingerprintMismatchContext(KEY, "stored", "current");

        // when / then
        assertThatThrownBy(() -> tested.handle(context))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(FingerprintMismatchException.class))
                .extracting(FingerprintMismatchException::getContext)
                .isSameAs(context);
    }

    @Test
    @DisplayName("UT hash() should give the same digest for the same bytes and a different one for different bytes")
    void hash_shouldGiveSameDigestForSameBytesAndDifferentOneForDifferentBytes() {
        // given
        byte [] one = "payload".getBytes(StandardCharsets.UTF_8);
        byte [] same = "payload".getBytes(StandardCharsets.UTF_8);
        byte [] other = "payloae".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThat(tested.hash(one)).isEqualTo(tested.hash(same));
        assertThat(tested.hash(one)).isNotEqualTo(tested.hash(other));
    }

    private static final class TestPolicy extends AbstractFingerprintPolicy {

        private TestPolicy(EmptyBodyFallback fallback) {
            super(fallback);
        }

        @Override
        public String generate(RequestContext context) {
            return hash(getRequestBodyWithFallback(context));
        }

        @Override
        public String hash(byte [] bytes) {
            return super.hash(bytes);
        }
    }
}
