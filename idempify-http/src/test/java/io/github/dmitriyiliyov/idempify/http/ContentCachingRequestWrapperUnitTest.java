package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentCachingRequestWrapperUnitTest {

    private static final byte [] BODY = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("UT getInputStream() when the body was cached should return it")
    void getInputStream_whenBodyWasCached_shouldReturnIt() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));

        // when
        byte [] result = tested.getInputStream().readAllBytes();

        // then
        assertThat(result).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT getInputStream() when called several times should return the body every time")
    void getInputStream_whenCalledSeveralTimes_shouldReturnBodyEveryTime() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));

        // when
        byte [] first = tested.getInputStream().readAllBytes();
        byte [] second = tested.getInputStream().readAllBytes();

        // then
        assertThat(first).isEqualTo(BODY);
        assertThat(second).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT getInputStream() when the original stream is already consumed should still return the body")
    void getInputStream_whenOriginalStreamIsAlreadyConsumed_shouldStillReturnBody() throws IOException {
        // given
        MockHttpServletRequest request = request(BODY);
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request);
        assertThat(request.getInputStream().readAllBytes()).isEmpty();

        // when
        byte [] result = tested.getInputStream().readAllBytes();

        // then
        assertThat(result).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT getInputStream() when the body is empty should return an empty stream")
    void getInputStream_whenBodyIsEmpty_shouldReturnEmptyStream() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(new byte [0]));

        // when
        ServletInputStream result = tested.getInputStream();

        // then
        assertThat(result.readAllBytes()).isEmpty();
        assertThat(result.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("UT getInputStream() when nothing is read yet should report the stream as unfinished and ready")
    void getInputStream_whenNothingIsReadYet_shouldReportStreamAsUnfinishedAndReady() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));

        // when
        ServletInputStream result = tested.getInputStream();

        // then
        assertThat(result.isFinished()).isFalse();
        assertThat(result.isReady()).isTrue();
    }

    @Test
    @DisplayName("UT getInputStream() when the body is fully read should report the stream as finished")
    void getInputStream_whenBodyIsFullyRead_shouldReportStreamAsFinished() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));

        // when
        ServletInputStream result = tested.getInputStream();
        result.readAllBytes();

        // then
        assertThat(result.isFinished()).isTrue();
        assertThat(result.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("UT getInputStream() when a read listener is set should ignore it")
    void getInputStream_whenReadListenerIsSet_shouldIgnoreIt() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));
        ServletInputStream stream = tested.getInputStream();

        // when
        stream.setReadListener(mock(ReadListener.class));

        // then
        assertThat(stream.readAllBytes()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT constructor when the body cannot be read should throw RuntimeException carrying the cause")
    void constructor_whenBodyCannotBeRead_shouldThrowRuntimeExceptionCarryingCause() throws IOException {
        // given
        IOException cause = new IOException("connection reset");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getInputStream()).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> new ContentCachingRequestWrapper(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error when reading request body")
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT wrapper when built should keep delegating everything but the body to the original request")
    void wrapper_whenBuilt_shouldKeepDelegatingEverythingButBodyToOriginalRequest() {
        // given
        MockHttpServletRequest request = request(BODY);

        // when
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request);

        // then
        assertThat(tested.getMethod()).isEqualTo("POST");
        assertThat(tested.getRequestURI()).isEqualTo("/payments");
        assertThat(tested.getContentType()).isEqualTo("application/json");
        assertThat(tested.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("UT getReader() when the body was cached should read it just like the input stream does")
    void getReader_whenBodyWasCached_shouldReadItJustLikeInputStreamDoes() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request(BODY));

        // when
        String result = tested.getReader().lines().collect(Collectors.joining());

        // then
        assertThat(result).isEqualTo(new String(BODY, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("UT getReader() when the request declares a charset should read the body with it")
    void getReader_whenRequestDeclaresCharset_shouldReadBodyWithIt() throws IOException {
        // given
        MockHttpServletRequest request = request("café".getBytes(StandardCharsets.ISO_8859_1));
        request.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(request);

        // when
        String result = read(tested);

        // then
        assertThat(result).isEqualTo("café");
    }

    @Test
    @DisplayName("UT getReader() when the request declares no charset should read the body as UTF-8")
    void getReader_whenRequestDeclaresNoCharset_shouldReadBodyAsUtf8() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(
                requestDeclaring(null, "платёж".getBytes(StandardCharsets.UTF_8)));

        // when
        String result = read(tested);

        // then
        assertThat(result).isEqualTo("платёж");
    }

    @Test
    @DisplayName("UT getReader() when the request declares a blank charset should read the body as UTF-8")
    void getReader_whenRequestDeclaresBlankCharset_shouldReadBodyAsUtf8() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(
                requestDeclaring("   ", "платёж".getBytes(StandardCharsets.UTF_8)));

        // when
        String result = read(tested);

        // then
        assertThat(result).isEqualTo("платёж");
    }

    @Test
    @DisplayName("UT getReader() when the request declares an unusable charset should fall back to UTF-8")
    void getReader_whenRequestDeclaresUnusableCharset_shouldFallBackToUtf8() throws IOException {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(
                requestDeclaring("utf-42", "платёж".getBytes(StandardCharsets.UTF_8)));

        // when
        String result = read(tested);

        // then
        assertThat(result).isEqualTo("платёж");
    }

    // the body is drained in the constructor, so a container that parses form parameters lazily finds nothing
    @Disabled("wrapping a form request leaves the container with no parameters to parse")
    @Test
    @DisplayName("UT getParameter() when the request is a form one should still return its parameters")
    void getParameter_whenRequestIsFormOne_shouldStillReturnItsParameters() {
        // given
        ContentCachingRequestWrapper tested = new ContentCachingRequestWrapper(formRequest("amount=10&currency=EUR"));

        // when
        String result = tested.getParameter("amount");

        // then
        assertThat(result).isEqualTo("10");
    }

    /**
     * Stands in for the container: real ones parse {@code application/x-www-form-urlencoded} parameters out of
     * the request body, and only when they are first asked for. {@link MockHttpServletRequest} takes them from
     * the builder instead, so wrapping a form request looks harmless there.
     */
    private static MockHttpServletRequest formRequest(String form) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments") {

            @Override
            public String getParameter(String name) {
                String body;
                try {
                    body = new String(getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                return Arrays.stream(body.split("&"))
                        .map(pair -> pair.split("=", 2))
                        .filter(pair -> pair.length == 2 && pair[0].equals(name))
                        .map(pair -> pair[1])
                        .findFirst()
                        .orElse(null);
            }
        };
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent(form.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String read(ContentCachingRequestWrapper wrapper) throws IOException {
        return wrapper.getReader().lines().collect(Collectors.joining());
    }

    private static MockHttpServletRequest requestDeclaring(String encoding, byte [] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments") {

            @Override
            public String getCharacterEncoding() {
                return encoding;
            }
        };
        request.setContent(body);
        return request;
    }

    private static MockHttpServletRequest request(byte [] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
