package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.OperationState;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAttributesOperationStateChannelUnitTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-08-09T12:05:00Z");
    private static final String ATTRIBUTE_NAME =
            HttpAttributesOperationStateChannel.class.getName() + ".IDEMPOTENT_OPERATION_STATE";

    private final HttpAttributesOperationStateChannel tested = new HttpAttributesOperationStateChannel();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("UT consume() when nothing was published should return null")
    void consume_whenNothingWasPublished_shouldReturnNull() {
        // given
        bindRequest();

        // when
        OperationState result = tested.consume();

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT consume() after publish() should return the published state")
    void consume_afterPublish_shouldReturnPublishedState() {
        // given
        bindRequest();
        OperationState published = TestOperationState.of(EXPIRES_AT, false);

        // when
        tested.publish(published);
        OperationState result = tested.consume();

        // then
        assertThat(result).isSameAs(published);
        assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.replayed()).isFalse();
    }

    @Test
    @DisplayName("UT publish() when a state was already published should replace it")
    void publish_whenStateWasAlreadyPublished_shouldReplaceIt() {
        // given
        bindRequest();
        OperationState last = TestOperationState.of(EXPIRES_AT, true);

        // when
        tested.publish(TestOperationState.of(EXPIRES_AT, false));
        tested.publish(last);

        // then
        assertThat(tested.consume()).isSameAs(last);
    }

    @Test
    @DisplayName("UT publish() should keep the state on the request itself rather than on the thread")
    void publish_shouldKeepStateOnRequestItselfRatherThanOnThread() {
        // given
        MockHttpServletRequest request = bindRequest();
        OperationState published = TestOperationState.of(EXPIRES_AT, false);

        // when
        tested.publish(published);

        // then
        assertThat(request.getAttribute(ATTRIBUTE_NAME)).isSameAs(published);
    }

    @Test
    @DisplayName("UT publish() should namespace the request attribute with its own class name")
    void publish_shouldNamespaceRequestAttributeWithItsOwnClassName() {
        // given
        MockHttpServletRequest request = bindRequest();

        // when
        tested.publish(TestOperationState.of(EXPIRES_AT, false));

        // then
        assertThat(Collections.list(request.getAttributeNames()))
                .allSatisfy(name -> assertThat(name)
                        .startsWith(HttpAttributesOperationStateChannel.class.getName()));
    }

    @Test
    @DisplayName("UT consume() when called twice should return null the second time")
    void consume_whenCalledTwice_shouldReturnNullSecondTime() {
        // given
        bindRequest();
        tested.publish(TestOperationState.of(EXPIRES_AT, false));

        // when
        OperationState first = tested.consume();
        OperationState second = tested.consume();

        // then
        assertThat(first).isNotNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("UT consume() should take the state off the request instead of leaving it there")
    void consume_shouldTakeStateOffRequestInsteadOfLeavingItThere() {
        // given
        MockHttpServletRequest request = bindRequest();
        tested.publish(TestOperationState.of(EXPIRES_AT, false));

        // when
        tested.consume();

        // then
        assertThat(request.getAttribute(ATTRIBUTE_NAME)).isNull();
    }

    @Test
    @DisplayName("UT consume() when another request is bound should not see the state of the previous one")
    void consume_whenAnotherRequestIsBound_shouldNotSeeStateOfPreviousOne() {
        // given
        bindRequest();
        tested.publish(TestOperationState.of(EXPIRES_AT, false));

        // when
        bindRequest();

        // then
        assertThat(tested.consume()).isNull();
    }

    @Test
    @DisplayName("UT publish() when no request is bound to the thread should throw IllegalStateException")
    void publish_whenNoRequestIsBoundToThread_shouldThrowIllegalStateException() {
        // given
        RequestContextHolder.resetRequestAttributes();

        // when / then
        assertThatThrownBy(() -> tested.publish(TestOperationState.of(EXPIRES_AT, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP request is bound to the current thread");
    }

    @Test
    @DisplayName("UT consume() when no request is bound to the thread should throw IllegalStateException")
    void consume_whenNoRequestIsBoundToThread_shouldThrowIllegalStateException() {
        // given
        RequestContextHolder.resetRequestAttributes();

        // when / then
        assertThatThrownBy(tested::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP request is bound to the current thread");
    }

    @Test
    @DisplayName("UT consume() when the bound request is already completed should throw IllegalStateException")
    void consume_whenBoundRequestIsAlreadyCompleted_shouldThrowIllegalStateException() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        tested.publish(TestOperationState.of(EXPIRES_AT, false));
        attributes.requestCompleted();

        // when / then
        assertThatThrownBy(tested::consume)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UT publish() should scope the state to the request and not to the session")
    void publish_shouldScopeStateToRequestAndNotToSession() {
        // given
        MockHttpServletRequest request = bindRequest();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        // when
        tested.publish(TestOperationState.of(EXPIRES_AT, false));

        // then
        assertThat(attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_SESSION)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    private static MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((HttpServletRequest) request));
        return request;
    }
}
