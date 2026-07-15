package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;
import io.github.dmitriyiliyov.springidempotency.core.RequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

public class DefaultFingerprintPolicyUnitTests {

    private final DefaultFingerprintPolicy tested = new DefaultFingerprintPolicy();

    @Test
    @DisplayName("UT generate() when context contains request data should return SHA-256 fingerprint")
    void generate_whenContextContainsRequestData_shouldReturnSha256Fingerprint() throws Exception {
        // given
        RequestContext context = new TestRequestContext("/test", "POST", "body");

        String source = "/test:POST:body";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String expected = HexFormat.of().formatHex(
                md.digest(source.getBytes(StandardCharsets.UTF_8))
        );

        // when
        String result = tested.generate(context);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("UT generate() when different contexts provided should return different fingerprints")
    void generate_whenDifferentContextsProvided_shouldReturnDifferentFingerprints() {
        // given
        RequestContext firstContext = new TestRequestContext("/first", "POST", "body");
        RequestContext secondContext = new TestRequestContext("/second", "POST", "body");

        // when
        String firstResult = tested.generate(firstContext);
        String secondResult = tested.generate(secondContext);

        // then
        assertThat(firstResult).isNotEqualTo(secondResult);
    }

    @Test
    @DisplayName("UT generate() when same context provided should return same fingerprint")
    void generate_whenSameContextProvided_shouldReturnSameFingerprint() {
        // given
        RequestContext firstContext = new TestRequestContext("/test", "POST", "body");
        RequestContext secondContext = new TestRequestContext("/test", "POST", "body");

        // when
        String firstResult = tested.generate(firstContext);
        String secondResult = tested.generate(secondContext);

        // then
        assertThat(firstResult).isEqualTo(secondResult);
    }

    @Test
    @DisplayName("UT generate() when context contains empty values should return fingerprint")
    void generate_whenContextContainsEmptyValues_shouldReturnFingerprint() {
        // given
        RequestContext context = new TestRequestContext("", "", "");

        // when
        String result = tested.generate(context);

        // then
        assertThat(result)
                .isNotNull()
                .hasSize(64);
    }

    @Test
    @DisplayName("UT compare() when fingerprints are equal should return true")
    void compare_whenFingerprintsAreEqual_shouldReturnTrue() {
        // given
        String previous = "fingerprint";
        String current = "fingerprint";

        // when
        boolean result = tested.compare(previous, current);

        // then
        assertTrue(result);
    }

    @Test
    @DisplayName("UT compare() when fingerprints are different should return false")
    void compare_whenFingerprintsAreDifferent_shouldReturnFalse() {
        // given
        String previous = "previous";
        String current = "current";

        // when
        boolean result = tested.compare(previous, current);

        // then
        assertFalse(result);
    }

    @Test
    @DisplayName("UT handle() should throw FingerprintMismatchException")
    void handle_shouldThrowFingerprintMismatchException() {
        // given
        DefaultFingerprintMismatchContext context = new DefaultFingerprintMismatchContext(
                UUID.randomUUID(),
                "previous",
                "current"
        );

        // when / then
        assertThatThrownBy(() -> tested.handle(context))
                .isInstanceOf(FingerprintMismatchException.class);
    }

    private static class TestRequestContext implements RequestContext {

        private final String path;
        private final String method;
        private final String body;

        private TestRequestContext(String path, String method, String body) {
            this.path = path;
            this.method = method;
            this.body = body;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public RequestType getRequestType() {
            return RequestType.HTTP;
        }

        @Override
        public String getHeader(String name) {
            return "none";
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getBody() {
            return body;
        }

        @Override
        public Object getNativeRequest() {
            return new Object();
        }
    }
}