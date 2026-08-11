package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import jakarta.servlet.http.MappingMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.mock.web.MockHttpServletMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentRequestMatcherUnitTest {

    private static final String METHOD = "POST";
    private static final String URI = "/payments";

    @Mock
    IdempotentHandlerRegistry handlerRegistry;

    @Mock
    OperationMetadataResolver metadataResolver;

    @InjectMocks
    DefaultIdempotentRequestMatcher tested;

    @Test
    @DisplayName("UT constructor when handlerRegistry is null should throw NullPointerException")
    void constructor_whenHandlerRegistryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentRequestMatcher(null, metadataResolver))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlerRegistry cannot be null");
    }

    @Test
    @DisplayName("UT constructor when metadataResolver is null should throw NullPointerException")
    void constructor_whenMetadataResolverIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentRequestMatcher(handlerRegistry, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadataResolver cannot be null");
    }

    @Test
    @DisplayName("UT match() when the request is null should return null")
    void match_whenRequestIsNull_shouldReturnNull() {
        // when
        OperationMetadata result = tested.match(null);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(handlerRegistry, metadataResolver);
    }

    @Test
    @DisplayName("UT match() when the registry knows no handler for the request should return null without resolving anything")
    void match_whenRegistryKnowsNoHandlerForRequest_shouldReturnNullWithoutResolvingAnything() {
        // given
        when(handlerRegistry.getHandlerMethod(eq(METHOD), any())).thenReturn(null);

        // when
        OperationMetadata result = tested.match(request(METHOD, URI));

        // then
        assertThat(result).isNull();
        verifyNoInteractions(metadataResolver);
    }

    @Test
    @DisplayName("UT match() when the registry knows the handler should resolve its metadata")
    void match_whenRegistryKnowsHandler_shouldResolveItsMetadata() throws NoSuchMethodException {
        // given
        OperationMetadata metadata = mock(OperationMetadata.class);
        when(handlerRegistry.getHandlerMethod(eq(METHOD), any())).thenReturn(handlerMethod("pay"));
        when(metadataResolver.resolve(method("pay"), TestController.class)).thenReturn(metadata);

        // when
        OperationMetadata result = tested.match(request(METHOD, URI));

        // then
        assertThat(result).isSameAs(metadata);
    }

    @Test
    @DisplayName("UT match() when the resolver has nothing to say about the handler should return null")
    void match_whenResolverHasNothingToSayAboutHandler_shouldReturnNull() throws NoSuchMethodException {
        // given
        when(handlerRegistry.getHandlerMethod(eq(METHOD), any())).thenReturn(handlerMethod("pay"));
        when(metadataResolver.resolve(method("pay"), TestController.class)).thenReturn(null);

        // when
        OperationMetadata result = tested.match(request(METHOD, URI));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT match() when the request is given should ask the registry with its own method and path")
    void match_whenRequestIsGiven_shouldAskRegistryWithItsOwnMethodAndPath() {
        // when
        tested.match(request("PATCH", "/orders/42/pay"));

        // then
        assertThat(askedPath("PATCH").value()).isEqualTo("/orders/42/pay");
    }

    @Test
    @DisplayName("UT match() when the application sits under a context path should ask the registry without it")
    void match_whenApplicationSitsUnderContextPath_shouldAskRegistryWithoutIt() {
        // given
        MockHttpServletRequest request = request(METHOD, "/app/payments");
        request.setContextPath("/app");

        // when
        tested.match(request);

        // then
        assertThat(askedPath(METHOD).value()).isEqualTo("/payments");
    }

    @Test
    @DisplayName("UT match() when the dispatcher servlet sits behind a prefix should ask the registry without it")
    void match_whenDispatcherServletSitsBehindPrefix_shouldAskRegistryWithoutIt() {
        // given
        MockHttpServletRequest request = request(METHOD, "/api/payments");
        request.setServletPath("/api");
        request.setHttpServletMapping(
                new MockHttpServletMapping("", "/api/*", "dispatcherServlet", MappingMatch.PATH));

        // when
        tested.match(request);

        // then
        assertThat(askedPath(METHOD).value()).isEqualTo("/payments");
    }

    private PathContainer askedPath(String method) {
        ArgumentCaptor<RequestPath> requestPath = ArgumentCaptor.forClass(RequestPath.class);
        verify(handlerRegistry).getHandlerMethod(eq(method), requestPath.capture());
        return requestPath.getValue().pathWithinApplication();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), method(methodName));
    }

    private Method method(String name) throws NoSuchMethodException {
        return TestController.class.getMethod(name);
    }

    public static class TestController {

        public String pay() {
            return "paid";
        }
    }
}
