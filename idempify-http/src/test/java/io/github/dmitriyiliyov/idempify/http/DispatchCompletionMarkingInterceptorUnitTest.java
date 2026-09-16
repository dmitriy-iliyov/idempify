package io.github.dmitriyiliyov.idempify.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchCompletionMarkingInterceptorUnitTest {

    private static final Object HANDLER = new Object();

    private final DispatchCompletionMarkingInterceptor tested = new DispatchCompletionMarkingInterceptor();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payments");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("UT afterCompletion() when no exception escaped the dispatch should mark the request as completed")
    void afterCompletion_whenNoExceptionEscapedDispatch_shouldMarkRequestAsCompleted() {
        // when
        tested.afterCompletion(request, response, HANDLER, null);

        // then
        assertThat(request.getAttribute(DispatchCompletionMarkingInterceptor.DISPATCH_COMPLETED_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("UT afterCompletion() when an exception escaped the dispatch should leave the request unmarked")
    void afterCompletion_whenExceptionEscapedDispatch_shouldLeaveRequestUnmarked() {
        // when
        tested.afterCompletion(request, response, HANDLER, new IllegalStateException("unresolved"));

        // then
        assertThat(request.getAttribute(DispatchCompletionMarkingInterceptor.DISPATCH_COMPLETED_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("UT getAndClearAttribute() when the request is marked should answer the mark and remove it")
    void getAndClearAttribute_whenRequestIsMarked_shouldAnswerMarkAndRemoveIt() {
        // given
        tested.afterCompletion(request, response, HANDLER, null);

        // when
        Boolean result = DispatchCompletionMarkingInterceptor.getAndClearAttribute(request);

        // then
        assertThat(result).isTrue();
        assertThat(request.getAttribute(DispatchCompletionMarkingInterceptor.DISPATCH_COMPLETED_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("UT getAndClearAttribute() when the request is unmarked should answer null")
    void getAndClearAttribute_whenRequestIsUnmarked_shouldAnswerNull() {
        // when
        Boolean result = DispatchCompletionMarkingInterceptor.getAndClearAttribute(request);

        // then
        assertThat(result).isNull();
    }
}
