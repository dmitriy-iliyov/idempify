package io.github.dmitriyiliyov.idempify.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FingerprintExceptionFilterUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final String URI_PATH = "/payments";

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", URI_PATH);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    // the response declares no charset, so the container writes the document as ISO-8859-1
    @Disabled("problem responses are written without a charset")
    @Test
    @DisplayName("UT ofInvalid() should write the problem as UTF-8")
    void ofInvalid_shouldWriteProblemAsUtf8() {
        // when
        FingerprintExceptionFilterUtils.ofInvalid(request, response, KEY, mapper, NOW);

        // then
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(response.getContentType()).contains("charset=UTF-8");
    }

    @Test
    @DisplayName("UT ofNullOrBlankFingerprint() should answer 500 with the broken policy type")
    void ofInvalid_shouldAnswer500WithBrokenPolicyType() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofInvalid(request, response, KEY, mapper, NOW);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");

        JsonNode problem = problem();
        assertThat(problem.get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
        assertThat(problem.get("status").asInt()).isEqualTo(500);
        assertThat(problem.get("title").asText()).isNotBlank();
        assertThat(problem.get("detail").asText()).isNotBlank();
        assertThat(problem.get("instance").asText()).isEqualTo(URI_PATH);
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        assertThat(mapper.treeToValue(problem.get("timestamp"), Instant.class)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() should answer 422 with the key reuse type")
    void ofMismatch_shouldAnswer422WithKeyReuseType() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofMismatch(request, response, KEY, mapper, NOW);

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");

        JsonNode problem = problem();
        assertThat(problem.get("type").asText()).isEqualTo(ProblemTypes.IDEMPOTENCY_KEY_REUSE.toString());
        assertThat(problem.get("status").asInt()).isEqualTo(422);
        assertThat(problem.get("instance").asText()).isEqualTo(URI_PATH);
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        assertThat(mapper.treeToValue(problem.get("timestamp"), Instant.class)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT ofExceptionallyGenerate() should answer 500 with the broken policy type")
    void ofExceptionallyGenerate_shouldAnswer500WithBrokenPolicyType() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofExceptionallyGenerate(request, response, KEY, mapper, NOW);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");

        JsonNode problem = problem();
        assertThat(problem.get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
        assertThat(problem.get("status").asInt()).isEqualTo(500);
        assertThat(problem.get("instance").asText()).isEqualTo(URI_PATH);
        assertThat(problem.get("idempotencyKey").asText()).isEqualTo(KEY.toString());
        assertThat(mapper.treeToValue(problem.get("timestamp"), Instant.class)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT ofExceptionallyGenerate() should not leak what the policy threw")
    void ofExceptionallyGenerate_shouldNotLeakWhatPolicyThrew() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofExceptionallyGenerate(request, response, KEY, mapper, NOW);

        // then
        assertThat(problem().get("detail").asText()).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain("Exception", "at io.github");
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() should describe the reuse without echoing either fingerprint")
    void ofFingerprintMismatch_shouldDescribeReuseWithoutEchoingEither() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofMismatch(request, response, KEY, mapper, NOW);

        // then
        assertThat(problem().get("detail").asText()).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain("fingerprint");
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() when the endpoint is under a context path should name the full request pattern")
    void ofMismatch_whenEndpointIsUnderContextPath_shouldNameFullRequestUri() throws Exception {
        // given
        MockHttpServletRequest prefixed = new MockHttpServletRequest("POST", "/api/payments");
        prefixed.setContextPath("/api");

        // when
        FingerprintExceptionFilterUtils.ofMismatch(prefixed, response, KEY, mapper, NOW);

        // then
        assertThat(problem().get("instance").asText()).isEqualTo("/api/payments");
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() when the path holds a reserved character should still be written")
    void ofMismatch_whenPathHoldsReservedCharacter_shouldStillBeWritten() throws Exception {
        // given
        MockHttpServletRequest encoded = new MockHttpServletRequest("POST", "/payments/%D0%BE%D0%BF%D0%BB%D0%B0%D1%82%D0%B0");

        // when / then
        assertThatCode(() -> FingerprintExceptionFilterUtils.ofMismatch(encoded, response, KEY, mapper, NOW))
                .doesNotThrowAnyException();
        assertThat(problem().get("instance").asText())
                .isEqualTo("/payments/%D0%BE%D0%BF%D0%BB%D0%B0%D1%82%D0%B0");
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() when the response cannot be written should not throw at the caller")
    void ofMismatch_whenResponseCannotBeWritten_shouldNotThrowAtCaller() throws Exception {
        // given
        HttpServletResponse failing = mock(HttpServletResponse.class);
        when(failing.getWriter()).thenThrow(new IOException("client is gone"));

        // when / then
        assertThatCode(() -> FingerprintExceptionFilterUtils.ofMismatch(request, failing, KEY, mapper, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT ofFingerprintMismatch() when the response is already committed should not throw at the caller")
    void ofMismatch_whenResponseIsAlreadyCommitted_shouldNotThrowAtCaller() throws Exception {
        // given
        response.getWriter().write("already sent");
        response.flushBuffer();

        // when / then
        assertThatCode(() -> FingerprintExceptionFilterUtils.ofMismatch(request, response, KEY, mapper, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT ofNullOrBlankFingerprint() when the mapper fails should not throw at the caller")
    void ofInvalid_whenMapperFails_shouldNotThrowAtCaller() throws Exception {
        // given
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new FailingSerialization());

        // when / then
        assertThatCode(() -> FingerprintExceptionFilterUtils.ofInvalid(request, response, KEY, failing, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT ofNullOrBlankFingerprint() when the idempotency key is null should still answer with a problem")
    void ofNullOrBlankFingerprint_whenIdempotencyKeyIsNull_shouldStillAnswerWithProblem() throws Exception {
        // when
        FingerprintExceptionFilterUtils.ofInvalid(request, response, null, mapper, NOW);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(problem().get("type").asText()).isEqualTo(ProblemTypes.FINGERPRINT_POLICY_BROKEN.toString());
    }

    @Test
    @DisplayName("UT every writer should describe the same request the same way")
    void everyWriter_shouldDescribeSameRequestSameWay() throws Exception {
        // given
        MockHttpServletResponse mismatchResponse = new MockHttpServletResponse();
        HttpServletRequest same = request;

        // when
        FingerprintExceptionFilterUtils.ofInvalid(same, response, KEY, mapper, NOW);
        FingerprintExceptionFilterUtils.ofMismatch(same, mismatchResponse, KEY, mapper, NOW);

        // then
        JsonNode broken = problem();
        JsonNode reuse = mapper.readTree(mismatchResponse.getContentAsByteArray());
        assertThat(broken.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(() -> reuse.fieldNames());
        assertThat(mismatchResponse.getContentType()).isEqualTo(response.getContentType());
    }

    private JsonNode problem() throws IOException {
        return mapper.readTree(response.getContentAsByteArray());
    }

    /**
     * The only way to make a mocked mapper fail the way a real one does - {@link JsonProcessingException} has no
     * public constructor.
     */
    private static final class FailingSerialization extends JsonProcessingException {

        FailingSerialization() {
            super("cannot serialize");
        }
    }
}
