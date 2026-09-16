package io.github.dmitriyiliyov.idempify.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchFailureMarkingResolverUnitTest {

    private final DispatchFailureMarkingResolver tested = new DispatchFailureMarkingResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("UT resolveException() when an exception reaches it should mark the request and leave resolution to others")
    void resolveException_whenExceptionReachesIt_shouldMarkRequestAndLeaveResolutionToOthers() {
        // when
        ModelAndView result = tested.resolveException(request, response, null, new IllegalStateException("boom"));

        // then
        assertThat(result).isNull();
        assertThat(DispatchFailureMarkingResolver.getAndClearAttribute(request)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("UT getOrder() should put it before every other resolver")
    void getOrder_shouldPutItBeforeEveryOtherResolver() {
        // when / then
        assertThat(tested.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("UT getAndClearAttribute() when the request is marked should remove the mark")
    void getAndClearAttribute_whenRequestIsMarked_shouldRemoveMark() {
        // given
        tested.resolveException(request, response, null, new IllegalStateException("boom"));
        DispatchFailureMarkingResolver.getAndClearAttribute(request);

        // when
        Boolean result = DispatchFailureMarkingResolver.getAndClearAttribute(request);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("UT getAndClearAttribute() when the request is unmarked should answer null")
    void getAndClearAttribute_whenRequestIsUnmarked_shouldAnswerNull() {
        // when
        Boolean result = DispatchFailureMarkingResolver.getAndClearAttribute(request);

        // then
        assertThat(result).isNull();
    }
}
