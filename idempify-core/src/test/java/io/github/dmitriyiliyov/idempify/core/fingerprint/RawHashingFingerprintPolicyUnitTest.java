package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.TestRequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawHashingFingerprintPolicyUnitTest {

    private final RawHashingFingerprintPolicy tested = new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());

    @Test
    @DisplayName("UT constructor when fallback is null should throw NullPointerException")
    void constructor_whenFallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new RawHashingFingerprintPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fallback cannot be null");
    }

    @Test
    @DisplayName("UT generate() when the fallback returns null should throw IllegalStateException naming it")
    void generate_whenFallbackReturnsNull_shouldThrowIllegalStateExceptionNamingIt() {
        // given
        RawHashingFingerprintPolicy tested = new RawHashingFingerprintPolicy(context -> null);
        RequestContext context = TestRequestContext.builder().bodyBytes(new byte[0]).build();

        // when / then
        assertThatThrownBy(() -> tested.generate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EmptyBodyFallback")
                .hasMessageContaining("returned null");
    }

    @Test
    @DisplayName("UT generate() when the fallback leaves the body out should fingerprint the path and method alone")
    void generate_whenFallbackLeavesBodyOut_shouldFingerprintPathAndMethodAlone() {
        // given
        RawHashingFingerprintPolicy tested = new RawHashingFingerprintPolicy(EmptyBodyFallback.NOOP);
        RequestContext bodyless = TestRequestContext.builder().bodyBytes(new byte[0]).build();
        RequestContext nullBodied = TestRequestContext.builder().bodyBytes(null).build();

        // when
        String result = tested.generate(bodyless);

        // then
        assertThat(result).isNotBlank();
        assertThat(result).isEqualTo(tested.generate(nullBodied));
    }

    @Test
    @DisplayName("UT generate() should hash the length-prefixed path and method followed by the raw body bytes")
    void generate_shouldHashLengthPrefixedPathAndMethodFollowedByRawBodyBytes() {
        // given
        RequestContext context = TestRequestContext.of("/payments", "POST", "body");

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|4|body"));
    }

    @Test
    @DisplayName("UT generate() when the body bytes are not valid text should still hash them")
    void generate_whenBodyBytesAreNotValidText_shouldStillHashThem() {
        // given
        byte[] body = {(byte) 0xC3, (byte) 0x28};
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").bodyBytes(body).build();

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex(concat("9|/payments|4|POST|2|", body)));
    }

    @Test
    @DisplayName("UT generate() when only the body differs should return a different fingerprint")
    void generate_whenOnlyBodyDiffers_shouldReturnDifferentFingerprint() {
        // given
        RequestContext one = TestRequestContext.of("/payments", "POST", "{\"amount\":10}");
        RequestContext other = TestRequestContext.of("/payments", "POST", "{\"amount\":11}");

        // when / then
        assertThat(tested.generate(one)).isNotEqualTo(tested.generate(other));
    }

    @Test
    @DisplayName("UT generate() when the same request is described twice should return the same fingerprint")
    void generate_whenSameRequestIsDescribedTwice_shouldReturnSameFingerprint() {
        // given
        RequestContext one = TestRequestContext.of("/payments", "POST", "body");
        RequestContext other = TestRequestContext.of("/payments", "POST", "body");

        // when / then
        assertThat(tested.generate(one)).isEqualTo(tested.generate(other));
    }

    @Test
    @DisplayName("UT generate() when the body is empty should hash the fallback body instead")
    void generate_whenBodyIsEmpty_shouldHashFallbackBodyInstead() {
        // given
        RawHashingFingerprintPolicy withFallback =
                new RawHashingFingerprintPolicy(context -> "fallback".getBytes(StandardCharsets.UTF_8));
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").body("").build();

        // when
        String result = withFallback.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|8|fallback"));
    }

    @Test
    @DisplayName("UT generate() when the body is empty and the fallback throws should let the failure out")
    void generate_whenBodyIsEmptyAndFallbackThrows_shouldLetFailureOut() {
        // given
        RequestContext context = TestRequestContext.builder().path("/payments").method("POST").body("").build();

        // when / then
        assertThatThrownBy(() -> tested.generate(context)).isInstanceOf(EmptyRequestBodyException.class);
    }

    private static byte[] concat(String prefix, byte[] body) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[prefixBytes.length + body.length];
        System.arraycopy(prefixBytes, 0, bytes, 0, prefixBytes.length);
        System.arraycopy(body, 0, bytes, prefixBytes.length, body.length);
        return bytes;
    }

    private static String sha256Hex(String source) {
        return sha256Hex(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
