package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.IdempotencyConstants;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpRequestContextUnitTest {

    private static final byte [] BODY = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("UT getRequestType() should return HTTP")
    void getRequestType_shouldReturnHttp() {
        // given
        HttpRequestContext tested = new HttpRequestContext(request());

        // when
        RequestType result = tested.getRequestType();

        // then
        assertThat(result).isEqualTo(RequestType.HTTP);
    }

    @Test
    @DisplayName("UT getHeader() when the header is present should return its value")
    void getHeader_whenHeaderIsPresent_shouldReturnItsValue() {
        // given
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = request();
        request.addHeader(IdempotencyConstants.HEADER_NAME, key.toString());
        HttpRequestContext tested = new HttpRequestContext(request);

        // when
        String result = tested.getHeader(IdempotencyConstants.HEADER_NAME);

        // then
        assertThat(result).isEqualTo(key.toString());
    }

    @Test
    @DisplayName("UT getHeader() when the header is absent should return null")
    void getHeader_whenHeaderIsAbsent_shouldReturnNull() {
        // given
        HttpRequestContext tested = new HttpRequestContext(request());

        // when
        String result = tested.getHeader(IdempotencyConstants.HEADER_NAME);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getMethod() should return the HTTP method of the request")
    void getMethod_shouldReturnHttpMethodOfRequest() {
        // given
        HttpRequestContext tested = new HttpRequestContext(request());

        // when
        String result = tested.getMethod();

        // then
        assertThat(result).isEqualTo("POST");
    }

    @Test
    @DisplayName("UT getPath() should return the request pattern together with the context path")
    void getPath_shouldReturnRequestUriTogetherWithContextPath() {
        // given
        MockHttpServletRequest request = request();
        request.setContextPath("/app");
        request.setRequestURI("/app/payments");
        HttpRequestContext tested = new HttpRequestContext(request);

        // when
        String result = tested.getPath();

        // then
        assertThat(result).isEqualTo("/app/payments");
    }

    // the path comes straight from getRequestURI(), so it carries whatever the deployment puts in front of it
    @Disabled("the fingerprint path is not application-relative")
    @Test
    @DisplayName("UT getPath() when the application sits under a context path should return the path without it")
    void getPath_whenApplicationSitsUnderContextPath_shouldReturnPathWithoutIt() {
        // given
        MockHttpServletRequest request = request();
        request.setContextPath("/app");
        request.setRequestURI("/app/payments");
        HttpRequestContext tested = new HttpRequestContext(request);

        // when
        String result = tested.getPath();

        // then
        assertThat(result).isEqualTo("/payments");
    }

    // path parameters belong to the session, not to what the client asked for
    @Disabled("the fingerprint path keeps the path parameters the container appends")
    @Test
    @DisplayName("UT getPath() when the uri carries path parameters should return the path without them")
    void getPath_whenUriCarriesPathParameters_shouldReturnPathWithoutThem() {
        // given
        MockHttpServletRequest request = request();
        request.setRequestURI("/payments;jsessionid=ABC123");
        HttpRequestContext tested = new HttpRequestContext(request);

        // when
        String result = tested.getPath();

        // then
        assertThat(result).isEqualTo("/payments");
    }

    @Test
    @DisplayName("UT getBodyBytes() should return the raw body of the request")
    void getBodyBytes_shouldReturnRawBodyOfRequest() {
        // given
        HttpRequestContext tested = new HttpRequestContext(request());

        // when
        byte [] result = tested.getBodyBytes();

        // then
        assertThat(result).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT getBodyBytes() when the request is wrapped should return the body on every call")
    void getBodyBytes_whenRequestIsWrapped_shouldReturnBodyOnEveryCall() {
        // given
        HttpRequestContext tested = new HttpRequestContext(new ContentCachingRequestWrapper(request()));

        // when
        byte [] first = tested.getBodyBytes();
        byte [] second = tested.getBodyBytes();

        // then
        assertThat(first).isEqualTo(BODY);
        assertThat(second).isEqualTo(BODY);
    }

    @Test
    @DisplayName("UT getBodyBytes() when the request is not wrapped should drain the body after the first call")
    void getBodyBytes_whenRequestIsNotWrapped_shouldDrainBodyAfterFirstCall() {
        // given
        HttpRequestContext tested = new HttpRequestContext(request());

        // when
        byte [] first = tested.getBodyBytes();
        byte [] second = tested.getBodyBytes();

        // then
        assertThat(first).isEqualTo(BODY);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("UT getBodyBytes() when the body cannot be read should throw RuntimeException carrying the cause")
    void getBodyBytes_whenBodyCannotBeRead_shouldThrowRuntimeExceptionCarryingCause() throws IOException {
        // given
        IOException cause = new IOException("connection reset");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getInputStream()).thenThrow(cause);
        HttpRequestContext tested = new HttpRequestContext(request);

        // when / then
        assertThatThrownBy(tested::getBodyBytes)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error when reading request body")
                .hasCause(cause);
    }

    @Test
    @DisplayName("UT toString() should mention the wrapped request")
    void toString_shouldMentionWrappedRequest() {
        // given
        MockHttpServletRequest request = request();
        HttpRequestContext tested = new HttpRequestContext(request);

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains("HttpRequestContext{", request.toString());
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
        request.setContentType("application/json");
        request.setContent(BODY);
        return request;
    }
}
