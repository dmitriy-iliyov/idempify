package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.TestRequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodyCanonicalizingFingerprintPolicyUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final BodyCanonicalizingFingerprintPolicy tested = policy(new UppercasingCanonicalizer());

    @Test
    @DisplayName("UT constructor when fallback is null should throw NullPointerException")
    void constructor_whenFallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new BodyCanonicalizingFingerprintPolicy(null, new UppercasingCanonicalizer()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fallback cannot be null");
    }

    @Test
    @DisplayName("UT constructor when bodyCanonicalizer is null should throw NullPointerException")
    void constructor_whenBodyCanonicalizerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new BodyCanonicalizingFingerprintPolicy(new ThrowingEmptyBodyFallback(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyCanonicalizer cannot be null");
    }

    @Test
    @DisplayName("UT generate() should hash the length-prefixed path, method and canonicalized body")
    void generate_shouldHashLengthPrefixedPathMethodAndCanonicalizedBody() {
        // given
        RequestContext context = TestRequestContext.of("/payments", "POST", "body");

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|4|BODY"));
    }

    @Test
    @DisplayName("UT generate() when the body is canonicalized to the same text should return the same fingerprint")
    void generate_whenBodyIsCanonicalizedToSameText_shouldReturnSameFingerprint() {
        // given
        RequestContext lowerCase = TestRequestContext.of("/payments", "POST", "body");
        RequestContext upperCase = TestRequestContext.of("/payments", "POST", "BODY");

        // when / then
        assertThat(tested.generate(lowerCase)).isEqualTo(tested.generate(upperCase));
    }

    @Test
    @DisplayName("UT generate() when the path differs should return a different fingerprint")
    void generate_whenPathDiffers_shouldReturnDifferentFingerprint() {
        // given
        RequestContext payments = TestRequestContext.of("/payments", "POST", "body");
        RequestContext orders = TestRequestContext.of("/orders", "POST", "body");

        // when / then
        assertThat(tested.generate(payments)).isNotEqualTo(tested.generate(orders));
    }

    @Test
    @DisplayName("UT generate() when the method differs should return a different fingerprint")
    void generate_whenMethodDiffers_shouldReturnDifferentFingerprint() {
        // given
        RequestContext post = TestRequestContext.of("/payments", "POST", "body");
        RequestContext put = TestRequestContext.of("/payments", "PUT", "body");

        // when / then
        assertThat(tested.generate(post)).isNotEqualTo(tested.generate(put));
    }

    @Test
    @DisplayName("UT generate() when a path and method boundary shifts should still return a different fingerprint")
    void generate_whenPathAndMethodBoundaryShifts_shouldStillReturnDifferentFingerprint() {
        // given
        RequestContext one = TestRequestContext.of("/pay", "MENTS", "body");
        RequestContext other = TestRequestContext.of("/payments", "POST", "body");

        // when / then
        assertThat(tested.generate(one)).isNotEqualTo(tested.generate(other));
    }

    @Test
    @DisplayName("UT generate() when the body is empty should hash the fallback body instead")
    void generate_whenBodyIsEmpty_shouldHashFallbackBodyInstead() {
        // given
        BodyCanonicalizingFingerprintPolicy withFallback = policy(new UppercasingCanonicalizer(), context -> "fallback".getBytes(StandardCharsets.UTF_8));
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").body("").build();

        // when
        String result = withFallback.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|8|FALLBACK"));
    }

    @Test
    @DisplayName("UT generate() when the body is null should hash the fallback body instead")
    void generate_whenBodyIsNull_shouldHashFallbackBodyInstead() {
        // given
        BodyCanonicalizingFingerprintPolicy withFallback = policy(new UppercasingCanonicalizer(), context -> "fallback".getBytes(StandardCharsets.UTF_8));
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").bodyBytes(null).build();

        // when
        String result = withFallback.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|8|FALLBACK"));
    }

    @Test
    @DisplayName("UT generate() when the body is empty and the fallback throws should let the failure out")
    void generate_whenBodyIsEmptyAndFallbackThrows_shouldLetFailureOut() {
        // given
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").body("").build();

        // when / then
        assertThatThrownBy(() -> tested.generate(context))
                .isInstanceOf(EmptyRequestBodyException.class)
                .hasMessageContaining("/payments")
                .hasMessageContaining("POST");
    }

    @Test
    @DisplayName("UT generate() when the path is blank should throw IllegalArgumentException")
    void generate_whenPathIsBlank_shouldThrowIllegalArgumentException() {
        // given
        RequestContext context = TestRequestContext.of("  ", "POST", "body");

        // when / then
        assertThatThrownBy(() -> tested.generate(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request path is null or blank");
    }

    @Test
    @DisplayName("UT generate() when the method is blank should throw IllegalArgumentException")
    void generate_whenMethodIsBlank_shouldThrowIllegalArgumentException() {
        // given
        RequestContext context = TestRequestContext.of("/payments", "  ", "body");

        // when / then
        assertThatThrownBy(() -> tested.generate(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request method is null or blank");
    }

    @Test
    @DisplayName("UT match() when the fingerprints are equal should return true")
    void match_whenFingerprintsAreEqual_shouldReturnTrue() {
        assertThat(tested.match("fingerprint", "fingerprint")).isTrue();
    }

    @Test
    @DisplayName("UT match() when the fingerprints differ should return false")
    void match_whenFingerprintsDiffer_shouldReturnFalse() {
        assertThat(tested.match("previous", "current")).isFalse();
    }

    @Test
    @DisplayName("UT match() when there is no previous fingerprint should return false")
    void match_whenThereIsNoPreviousFingerprint_shouldReturnFalse() {
        assertThat(tested.match(null, "current")).isFalse();
    }

    @Test
    @DisplayName("UT handle() should throw FingerprintMismatchException carrying the mismatch context")
    void handle_shouldThrowFingerprintMismatchExceptionCarryingMismatchContext() {
        // given
        FingerprintMismatchContext context = new DefaultFingerprintMismatchContext(KEY, "previous", "current");

        // when / then
        assertThatThrownBy(() -> tested.handle(context))
                .isInstanceOf(FingerprintMismatchException.class)
                .extracting(thrown -> ((FingerprintMismatchException) thrown).getContext())
                .isSameAs(context);
    }

    @Test
    @DisplayName("UT generate() when the fallback leaves the body out should not reach the canonicalizer at all")
    void generate_whenFallbackLeavesBodyOut_shouldNotReachCanonicalizerAtAll() {
        // given
        ThrowingCanonicalizer canonicalizer = new ThrowingCanonicalizer();
        BodyCanonicalizingFingerprintPolicy tested = policy(canonicalizer, EmptyBodyFallback.NOOP);
        RequestContext context = TestRequestContext.builder().bodyBytes(new byte[0]).build();

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isNotBlank();
        assertThat(canonicalizer.calls).isZero();
    }

    private BodyCanonicalizingFingerprintPolicy policy(BodyCanonicalizer canonicalizer) {
        return policy(canonicalizer, new ThrowingEmptyBodyFallback());
    }

    private BodyCanonicalizingFingerprintPolicy policy(BodyCanonicalizer canonicalizer, EmptyBodyFallback fallback) {
        return new BodyCanonicalizingFingerprintPolicy(fallback, canonicalizer);
    }

    private static String sha256Hex(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Canonicalization narrow enough for the expected hash to be written out by hand.
     */
    private static final class UppercasingCanonicalizer implements BodyCanonicalizer {

        @Override
        public String canonicalize(byte[] body) {
            return new String(body, StandardCharsets.UTF_8).toUpperCase();
        }
    }

    /**
     * Stands in for a canonicalizer that cannot read an empty body, which is what every real one is.
     */
    private static final class ThrowingCanonicalizer implements BodyCanonicalizer {

        private int calls;

        @Override
        public String canonicalize(byte[] body) {
            calls++;
            throw new IllegalArgumentException("nothing to canonicalize");
        }
    }

    @Test
    @DisplayName("UT EmptyRequestBodyException should name the request it refused")
    void emptyRequestBodyException_shouldNameRequestItRefused() {
        // given
        RequestContext context = io.github.dmitriyiliyov.idempify.core.TestRequestContext.of("/payments", "POST", null);

        // when
        EmptyRequestBodyException tested = new EmptyRequestBodyException(context);

        // then
        assertThat(tested.getMessage()).contains("/payments");
    }
}
