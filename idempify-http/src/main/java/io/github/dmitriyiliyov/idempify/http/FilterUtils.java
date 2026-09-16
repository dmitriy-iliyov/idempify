package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.util.UUID;

public final class FilterUtils {

    private static final Logger log = LoggerFactory.getLogger(FilterUtils.class);

    private FilterUtils() { }

    /**
     * Whether an exception reached the resolvers of {@link DispatcherServlet}, so that the response may now hold
     * a resolver's answer rather than the handler's. {@link DispatcherServlet#EXCEPTION_ATTRIBUTE} is checked
     * besides the mark of {@link DispatchFailureMarkingResolver} for a resolver of the application that is ordered
     * as high and answers before the mark is set.
     */
    public static boolean wasDispatchExceptionally(HttpServletRequest request) {
        return request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null
                || DispatchFailureMarkingResolver.getAndClearAttribute(request) != null;
    }

    /**
     * Whether {@link DispatcherServlet} finished without an exception escaping it. {@code false} as well on the
     * first dispatch of an asynchronous handler, and whenever {@link DispatchCompletionMarkingInterceptor} is not
     * among the interceptors of the mapping that served the request.
     */
    public static boolean wasDispatchCompleted(HttpServletRequest request) {
        return DispatchCompletionMarkingInterceptor.getAndClearAttribute(request) != null;
    }

    public static void writeToServletResponse(UUID idempotencyKey,
                                              HttpServletResponse response,
                                              Response responseView) throws IOException {
        response.setStatus(responseView.getStatus());

        String contentType = responseView.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            response.setContentType(contentType);
        } else {
            log.warn("Operation (idempotencyKey={}) response contentType is null or blank", idempotencyKey);
        }

        responseView.getHeaders().forEach(response::setHeader);

        byte [] body = responseView.getBody();
        int length = body == null ? 0 : body.length;
        response.setContentLength(length);

        if (length > 0) {
            response.getOutputStream().write(body);
        }
    }
}
