package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Map;

import static io.github.dmitriyiliyov.idempify.http.HttpConstants.SUPPORTED_METHODS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotentHandlerRegistryUnitTest {

    private static final String POST = "POST";

    @Mock
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("UT constructor when handlerMapping is null should throw NullPointerException")
    void constructor_whenHandlerMappingIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotentHandlerRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handlerMapping cannot be null");
    }

    @Test
    @DisplayName("UT getHandlerMethod() before afterSingletonsInstantiated() should return null")
    void getHandlerMethod_beforeAfterSingletonsInstantiated_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = new DefaultIdempotentHandlerRegistry(handlerMapping);

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/payments"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the method is null should return null")
    void getHandlerMethod_whenMethodIsNull_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(idempotentEndpoint("/payments"));

        // when
        HandlerMethod result = tested.getHandlerMethod(null, requestPath("/payments"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the method is blank should return null")
    void getHandlerMethod_whenMethodIsBlank_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(idempotentEndpoint("/payments"));

        // when
        HandlerMethod result = tested.getHandlerMethod("   ", requestPath("/payments"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the request path is null should return null")
    void getHandlerMethod_whenRequestPathIsNull_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(idempotentEndpoint("/payments"));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the path is an unknown one should return null")
    void getHandlerMethod_whenPathIsUnknownOne_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(idempotentEndpoint("/payments"));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/orders"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the request matches an idempotent endpoint should return its handler")
    void getHandlerMethod_whenRequestMatchesIdempotentEndpoint_shouldReturnItsHandler() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/payments"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the path matches but the http method does not should return null")
    void getHandlerMethod_whenPathMatchesButHttpMethodDoesNot_shouldReturnNull() {
        // given
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(idempotentEndpoint("/payments"));

        // when
        HandlerMethod result = tested.getHandlerMethod("PUT", requestPath("/payments"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the http method is lowercase should still find the handler")
    void getHandlerMethod_whenHttpMethodIsLowercase_shouldStillFindHandler() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod("post", requestPath("/payments"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the mapping declares several http methods should answer for each of them")
    void getHandlerMethod_whenMappingDeclaresSeveralHttpMethods_shouldAnswerForEachOfThem() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        RequestMappingInfo mappingInfo = RequestMappingInfo.paths("/payments")
                .methods(RequestMethod.POST, RequestMethod.PATCH)
                .build();
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(mappingInfo, handlerMethod));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isSameAs(handlerMethod);
        assertThat(tested.getHandlerMethod("PATCH", requestPath("/payments"))).isSameAs(handlerMethod);
        assertThat(tested.getHandlerMethod("PUT", requestPath("/payments"))).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the mapping restricts no http method should answer for every supported one")
    void getHandlerMethod_whenMappingRestrictsNoHttpMethod_shouldAnswerForEverySupportedOne() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        RequestMappingInfo mappingInfo = RequestMappingInfo.paths("/payments").build();
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(mappingInfo, handlerMethod));

        // when / then
        assertThat(SUPPORTED_METHODS).allSatisfy(method ->
                assertThat(tested.getHandlerMethod(method.name(), requestPath("/payments"))).isSameAs(handlerMethod));
        assertThat(tested.getHandlerMethod("GET", requestPath("/payments"))).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the endpoint serves an unsupported http method only should never answer for it")
    void getHandlerMethod_whenEndpointServesUnsupportedHttpMethodOnly_shouldNeverAnswerForIt() {
        // given
        RequestMappingInfo mappingInfo = mappingInfo(RequestMethod.GET, "/payments");
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(mappingInfo, handlerMethod(true)));

        // when / then
        assertThat(tested.getHandlerMethod("GET", requestPath("/payments"))).isNull();
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when no method is annotated with Idempotent should return null for all of them")
    void getHandlerMethod_whenNoMethodIsAnnotatedWithIdempotent_shouldReturnNullForAllOfThem() {
        // given
        RequestMappingInfo mappingInfo = mappingInfo(RequestMethod.POST, "/payments");
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(mappingInfo, handlerMethod(false)));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/payments"));

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when only some methods are annotated with Idempotent should answer for annotated ones only")
    void getHandlerMethod_whenOnlySomeMethodsAreAnnotatedWithIdempotent_shouldAnswerForAnnotatedOnesOnly() {
        // given
        HandlerMethod idempotent = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(
                mappingInfo(RequestMethod.POST, "/payments"), idempotent,
                mappingInfo(RequestMethod.POST, "/refunds"), handlerMethod(false)
        ));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isSameAs(idempotent);
        assertThat(tested.getHandlerMethod(POST, requestPath("/refunds"))).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the mapping has several patterns should answer for every one of them")
    void getHandlerMethod_whenMappingHasSeveralPatterns_shouldAnswerForEveryOneOfThem() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(
                Map.of(mappingInfo(RequestMethod.POST, "/payments", "/v2/payments"), handlerMethod));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isSameAs(handlerMethod);
        assertThat(tested.getHandlerMethod(POST, requestPath("/v2/payments"))).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when several endpoints are registered should return the handler of each of them")
    void getHandlerMethod_whenSeveralEndpointsAreRegistered_shouldReturnHandlerOfEachOfThem() {
        // given
        HandlerMethod payments = handlerMethod(true);
        HandlerMethod orders = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(
                mappingInfo(RequestMethod.POST, "/payments"), payments,
                mappingInfo(RequestMethod.POST, "/orders/{id}/pay"), orders
        ));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isSameAs(payments);
        assertThat(tested.getHandlerMethod(POST, requestPath("/orders/42/pay"))).isSameAs(orders);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the pattern is templated should match the concrete path behind it")
    void getHandlerMethod_whenPatternIsTemplated_shouldMatchConcretePathBehindIt() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(
                Map.of(mappingInfo(RequestMethod.POST, "/orders/{id}/pay"), handlerMethod));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/orders/42/pay"))).isSameAs(handlerMethod);
        assertThat(tested.getHandlerMethod(POST, requestPath("/orders/42/cancel"))).isNull();
    }

    @Test
    @DisplayName("UT getHandlerMethod() when several patterns match should take the most specific one")
    void getHandlerMethod_whenSeveralPatternsMatch_shouldTakeMostSpecificOne() {
        // given
        HandlerMethod catchAll = handlerMethod(true);
        HandlerMethod specific = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(
                mappingInfo(RequestMethod.POST, "/orders/**"), catchAll,
                mappingInfo(RequestMethod.POST, "/orders/{id}/pay"), specific
        ));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/orders/42/pay"));

        // then
        assertThat(result).isSameAs(specific);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the request path carries a context path should match without it")
    void getHandlerMethod_whenRequestPathCarriesContextPath_shouldMatchWithoutIt() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, RequestPath.parse("/shop/payments", "/shop"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the path carries parameters should match the segment without them")
    void getHandlerMethod_whenPathCarriesParameters_shouldMatchSegmentWithoutThem() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/payments;jsessionid=ABC123"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when a literal segment is percent-encoded should match it decoded")
    void getHandlerMethod_whenLiteralSegmentIsPercentEncoded_shouldMatchItDecoded() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments/оплата"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(
                POST, requestPath("/payments/%D0%BE%D0%BF%D0%BB%D0%B0%D1%82%D0%B0"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT afterSingletonsInstantiated() when the handler mapping carries its own parser should read the patterns with it")
    void afterSingletonsInstantiated_whenHandlerMappingCarriesOwnParser_shouldReadPatternsWithIt() {
        // given
        PathPatternParser caseInsensitive = new PathPatternParser();
        caseInsensitive.setCaseSensitive(false);
        when(handlerMapping.getPatternParser()).thenReturn(caseInsensitive);

        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, "/payments"), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/PAYMENTS"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the idempotent endpoint is mapped to the application root should register it")
    void getHandlerMethod_whenIdempotentEndpointIsMappedToApplicationRoot_shouldRegisterIt() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        DefaultIdempotentHandlerRegistry tested =
                initializedRegistry(Map.of(mappingInfo(RequestMethod.POST, ""), handlerMethod));

        // when
        HandlerMethod result = tested.getHandlerMethod(POST, requestPath("/"));

        // then
        assertThat(result).isSameAs(handlerMethod);
    }

    @Test
    @DisplayName("UT getHandlerMethod() when the mapping carries no pattern of its own should register it at the application root")
    void getHandlerMethod_whenMappingCarriesNoPatternOfItsOwn_shouldRegisterItAtApplicationRoot() {
        // given
        HandlerMethod handlerMethod = handlerMethod(true);
        RequestMappingInfo mappingInfo = RequestMappingInfo.paths().methods(RequestMethod.POST).build();
        DefaultIdempotentHandlerRegistry tested = initializedRegistry(Map.of(mappingInfo, handlerMethod));

        // when / then
        assertThat(tested.getHandlerMethod(POST, requestPath("/"))).isSameAs(handlerMethod);
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isNull();
    }

    @Test
    @DisplayName("UT afterSingletonsInstantiated() when called twice should replace the registrations instead of accumulating")
    void afterSingletonsInstantiated_whenCalledTwice_shouldReplaceRegistrationsInsteadOfAccumulating() {
        // given
        HandlerMethod orders = handlerMethod(true);
        Map<RequestMappingInfo, HandlerMethod> first = idempotentEndpoint("/payments");
        Map<RequestMappingInfo, HandlerMethod> second = Map.of(mappingInfo(RequestMethod.POST, "/orders"), orders);

        when(handlerMapping.getHandlerMethods()).thenReturn(first, second);
        DefaultIdempotentHandlerRegistry tested = new DefaultIdempotentHandlerRegistry(handlerMapping);

        // when
        tested.afterSingletonsInstantiated();
        tested.afterSingletonsInstantiated();

        // then
        assertThat(tested.getHandlerMethod(POST, requestPath("/payments"))).isNull();
        assertThat(tested.getHandlerMethod(POST, requestPath("/orders"))).isSameAs(orders);
    }

    private DefaultIdempotentHandlerRegistry initializedRegistry(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
        when(handlerMapping.getHandlerMethods()).thenReturn(handlerMethods);
        DefaultIdempotentHandlerRegistry registry = new DefaultIdempotentHandlerRegistry(handlerMapping);
        registry.afterSingletonsInstantiated();
        return registry;
    }

    private static RequestPath requestPath(String value) {
        return RequestPath.parse(value, null);
    }

    private static Map<RequestMappingInfo, HandlerMethod> idempotentEndpoint(String... patterns) {
        return Map.of(mappingInfo(RequestMethod.POST, patterns), handlerMethod(true));
    }

    private static RequestMappingInfo mappingInfo(RequestMethod method, String... patterns) {
        return RequestMappingInfo.paths(patterns).methods(method).build();
    }

    private static HandlerMethod handlerMethod(boolean idempotent) {
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.hasMethodAnnotation(Idempotent.class)).thenReturn(idempotent);
        return handlerMethod;
    }
}
