package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCachedResponseUnitTest {

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("UT getters should hand back everything the response was built with")
    void getters_shouldHandBackEverythingResponseWasBuiltWith() {
        // given
        DefaultCachedResponse tested = new DefaultCachedResponse(201, BODY, "application/json", "fingerprint");

        // then
        assertThat(tested.getStatus()).isEqualTo(201);
        assertThat(tested.getBody()).isEqualTo(BODY);
        assertThat(tested.getContentType()).isEqualTo("application/json");
        assertThat(tested.getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT equals() when the status and the body agree should treat the responses as equal")
    void equals_whenStatusAndBodyAgree_shouldTreatResponsesAsEqual() {
        // given
        DefaultCachedResponse one = new DefaultCachedResponse(201, BODY, "application/json", "fingerprint");
        DefaultCachedResponse other = new DefaultCachedResponse(
                201, "{\"id\":1}".getBytes(StandardCharsets.UTF_8), "application/json", "fingerprint");

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the status differs should treat the responses as different")
    void equals_whenStatusDiffers_shouldTreatResponsesAsDifferent() {
        assertThat(new DefaultCachedResponse(201, BODY, "application/json", "fingerprint"))
                .isNotEqualTo(new DefaultCachedResponse(200, BODY, "application/json", "fingerprint"));
    }

    @Test
    @DisplayName("UT equals() when the body differs should treat the responses as different")
    void equals_whenBodyDiffers_shouldTreatResponsesAsDifferent() {
        assertThat(new DefaultCachedResponse(201, BODY, "application/json", "fingerprint"))
                .isNotEqualTo(new DefaultCachedResponse(
                        201, "{\"id\":2}".getBytes(StandardCharsets.UTF_8), "application/json", "fingerprint"));
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultCachedResponse(201, BODY, "application/json", "fingerprint"))
                .isNotEqualTo("not a response");
    }

    @Test
    @DisplayName("UT toString() should name the status and the body length")
    void toString_shouldNameStatusAndBodyLength() {
        assertThat(new DefaultCachedResponse(201, BODY, "application/json", "fingerprint").toString())
                .contains("status=201", "bodyLength=" + BODY.length);
    }

    @Test
    @DisplayName("UT toString() when there is no body should report a zero length")
    void toString_whenThereIsNoBody_shouldReportZeroLength() {
        assertThat(new DefaultCachedResponse(204, null, null, null).toString()).contains("bodyLength=0");
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultCachedResponse(200, new byte[]{1}, "text/plain", null))
                .isNotEqualTo("not a response");
    }
}
