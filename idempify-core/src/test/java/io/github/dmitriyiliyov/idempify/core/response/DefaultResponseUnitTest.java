package io.github.dmitriyiliyov.idempify.core.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResponseUnitTest {

    private static final Map<String, String> HEADERS = Map.of("Location", "/orders/1");

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("UT getters should hand back everything the response was built with")
    void getters_shouldHandBackEverythingResponseWasBuiltWith() {
        // given
        DefaultResponse tested = new DefaultResponse(201, BODY, "application/json", HEADERS);

        // then
        assertThat(tested.getStatus()).isEqualTo(201);
        assertThat(tested.getBody()).isEqualTo(BODY);
        assertThat(tested.getContentType()).isEqualTo("application/json");
        assertThat(tested.getHeaders()).isEqualTo(HEADERS);
    }

    @Test
    @DisplayName("UT equals() when the status and the body agree should treat the responses as equal")
    void equals_whenStatusAndBodyAgree_shouldTreatResponsesAsEqual() {
        // given
        DefaultResponse one = new DefaultResponse(201, BODY, "application/json", HEADERS);
        DefaultResponse other = new DefaultResponse(
                201, "{\"id\":1}".getBytes(StandardCharsets.UTF_8), "application/json", HEADERS);

        // then
        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }

    @Test
    @DisplayName("UT equals() when the status differs should treat the responses as different")
    void equals_whenStatusDiffers_shouldTreatResponsesAsDifferent() {
        assertThat(new DefaultResponse(201, BODY, "application/json", HEADERS))
                .isNotEqualTo(new DefaultResponse(200, BODY, "application/json", HEADERS));
    }

    @Test
    @DisplayName("UT equals() when the body differs should treat the responses as different")
    void equals_whenBodyDiffers_shouldTreatResponsesAsDifferent() {
        assertThat(new DefaultResponse(201, BODY, "application/json", HEADERS))
                .isNotEqualTo(new DefaultResponse(
                        201, "{\"id\":2}".getBytes(StandardCharsets.UTF_8), "application/json", HEADERS));
    }

    @Test
    @DisplayName("UT equals() when compared to another type should not be equal")
    void equals_whenComparedToAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultResponse(201, BODY, "application/json", HEADERS))
                .isNotEqualTo("not a response");
    }

    @Test
    @DisplayName("UT toString() should name the status and the body length")
    void toString_shouldNameStatusAndBodyLength() {
        assertThat(new DefaultResponse(201, BODY, "application/json", HEADERS).toString())
                .contains("status=201", "bodyLength=" + BODY.length);
    }

    @Test
    @DisplayName("UT toString() when there is no body should report a zero length")
    void toString_whenThereIsNoBody_shouldReportZeroLength() {
        assertThat(new DefaultResponse(204, null, null, null).toString()).contains("bodyLength=0");
    }

    @Test
    @DisplayName("UT equals() when compared with another type should not be equal")
    void equals_whenComparedWithAnotherType_shouldNotBeEqual() {
        assertThat(new DefaultResponse(200, new byte[]{1}, "text/plain", null))
                .isNotEqualTo("not a response");
    }
}
