package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Collects the answer the servlet produced into the form the record keeps. The interesting part is the header
 * policy: an empty inclusion set keeps everything, a named one keeps only what it names, and the exclusion set
 * is subtracted in both cases - so a header named in both is dropped.
 */
class HttpResponseProviderUnitTest {

    private static final byte [] BODY = "{\"paid\":10}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("UT constructor() when the policy is null should throw NullPointerException")
    void constructor_whenPolicyIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new HttpResponseProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy cannot be null");
    }

    @Test
    @DisplayName("UT provide() should carry the status, the body and the content type as the servlet left them")
    void provide_shouldCarryStatusBodyAndContentTypeAsServletLeftThem() throws IOException {
        // given
        ContentCachingResponseWrapper wrapper = answered();

        // when
        Response result = new HttpResponseProvider(keepingEverything()).provide(wrapper);

        // then
        assertThat(result.getStatus()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(BODY);
        assertThat(result.getContentType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("UT provide() when the policy names no header should keep every header the answer carried")
    void provide_whenPolicyNamesNoHeader_shouldKeepEveryHeaderAnswerCarried() throws IOException {
        // when
        Response result = new HttpResponseProvider(keepingEverything()).provide(answered());

        // then
        assertThat(result.getHeaders())
                .containsEntry("Location", "/payments/1")
                .containsEntry("X-Trace", "abc");
    }

    @Test
    @DisplayName("UT provide() when the policy names headers should keep only those")
    void provide_whenPolicyNamesHeaders_shouldKeepOnlyThose() throws IOException {
        // given
        ResponseProvidePolicy policy = ResponseConfig.builder()
                .includedHeaders(Set.of("Location"))
                .build();

        // when
        Response result = new HttpResponseProvider(policy).provide(answered());

        // then
        assertThat(result.getHeaders()).containsOnlyKeys("Location");
    }

    @Test
    @DisplayName("UT provide() when a header is excluded should drop it though nothing else was named")
    void provide_whenHeaderIsExcluded_shouldDropItThoughNothingElseWasNamed() throws IOException {
        // given
        ResponseProvidePolicy policy = ResponseConfig.builder()
                .excludedHeaders(Set.of("X-Trace"))
                .build();

        // when
        Response result = new HttpResponseProvider(policy).provide(answered());

        // then
        assertThat(result.getHeaders())
                .containsKey("Location")
                .doesNotContainKey("X-Trace");
    }

    @Test
    @DisplayName("UT provide() when a header is both included and excluded should let the exclusion win")
    void provide_whenHeaderIsBothIncludedAndExcluded_shouldLetExclusionWin() throws IOException {
        // given
        ResponseProvidePolicy policy = ResponseConfig.builder()
                .includedHeaders(Set.of("Location", "X-Trace"))
                .excludedHeaders(Set.of("X-Trace"))
                .build();

        // when
        Response result = new HttpResponseProvider(policy).provide(answered());

        // then
        assertThat(result.getHeaders()).containsOnlyKeys("Location");
    }

    @Test
    @DisplayName("UT provide() when the policy names a header in another case should still keep it")
    void provide_whenPolicyNamesHeaderInAnotherCase_shouldStillKeepIt() throws IOException {
        // given - header names are case-insensitive in HTTP, so a config saying "location" names the same
        // header the servlet set as "Location"
        ResponseProvidePolicy policy = ResponseConfig.builder()
                .includedHeaders(Set.of("location"))
                .build();

        // when
        Response result = new HttpResponseProvider(policy).provide(answered());

        // then
        assertThat(result.getHeaders()).containsOnlyKeys("Location");
    }

    @Test
    @DisplayName("UT provide() when the answer carried no header should keep an empty set of them")
    void provide_whenAnswerCarriedNoHeader_shouldKeepEmptySetOfThem() throws IOException {
        // given
        MockHttpServletResponse raw = new MockHttpServletResponse();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(raw);
        wrapper.setStatus(204);

        // when
        Response result = new HttpResponseProvider(keepingEverything()).provide(wrapper);

        // then
        assertThat(result.getHeaders()).isEmpty();
    }

    private static ContentCachingResponseWrapper answered() throws IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        wrapper.setStatus(201);
        wrapper.setContentType("application/json");
        wrapper.addHeader("Location", "/payments/1");
        wrapper.addHeader("X-Trace", "abc");
        wrapper.getOutputStream().write(BODY);
        return wrapper;
    }

    private static ResponseProvidePolicy keepingEverything() {
        return ResponseConfig.builder().build();
    }
}
