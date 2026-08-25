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

class BytesNormalizingFingerprintPolicyUnitTest {

    /** "cafe" with a single precomposed e-acute. */
    private static final String PRECOMPOSED = "caf" + (char) 0x00E9;

    /** The same text spelled as a plain "e" followed by a combining acute accent. */
    private static final String DECOMPOSED = "cafe" + (char) 0x0301;

    private final BytesNormalizingFingerprintPolicy tested =
            new BytesNormalizingFingerprintPolicy(new ThrowingEmptyBodyFallback());

    @Test
    @DisplayName("UT constructor when fallback is null should throw NullPointerException")
    void constructor_whenFallbackIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new BytesNormalizingFingerprintPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fallback cannot be null");
    }

    @Test
    @DisplayName("UT generate() should hash the length-prefixed path, method and NFC-normalized body")
    void generate_shouldHashLengthPrefixedPathMethodAndNfcNormalizedBody() {
        // given
        RequestContext context = TestRequestContext.of("/payments", "POST", "body");

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isEqualTo(sha256Hex("9|/payments|4|POST|4|body"));
    }

    @Test
    @DisplayName("UT generate() when the same text is composed differently should return the same fingerprint")
    void generate_whenSameTextIsComposedDifferently_shouldReturnSameFingerprint() {
        // given
        RequestContext precomposed = TestRequestContext.of("/payments", "POST", PRECOMPOSED);
        RequestContext decomposed = TestRequestContext.of("/payments", "POST", DECOMPOSED);

        // when / then
        assertThat(tested.generate(precomposed)).isEqualTo(tested.generate(decomposed));
    }

    @Test
    @DisplayName("UT generate() when the text really differs should return a different fingerprint")
    void generate_whenTextReallyDiffers_shouldReturnDifferentFingerprint() {
        // given
        RequestContext one = TestRequestContext.of("/payments", "POST", PRECOMPOSED);
        RequestContext other = TestRequestContext.of("/payments", "POST", "cafe");

        // when / then
        assertThat(tested.generate(one)).isNotEqualTo(tested.generate(other));
    }

    @Test
    @DisplayName("UT generate() when the body is empty should hash the fallback body instead")
    void generate_whenBodyIsEmpty_shouldHashFallbackBodyInstead() {
        // given
        BytesNormalizingFingerprintPolicy withFallback =
                new BytesNormalizingFingerprintPolicy(context -> "fallback".getBytes(StandardCharsets.UTF_8));
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

    private static String sha256Hex(String source) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
