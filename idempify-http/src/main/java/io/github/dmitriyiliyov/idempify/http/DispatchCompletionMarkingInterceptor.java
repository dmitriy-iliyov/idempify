package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Marks a request whose {@code DispatcherServlet} finished without an exception escaping it. An exception that
 * leaves the servlet unresolved - and a failed rendering of a view - reaches {@code afterCompletion} as
 * {@code ex} and leaves the request unmarked, so a filter between {@link OperationResponseFilter} and the
 * servlet that answers such an exception itself cannot pass its answer off as the one of the operation.
 * <p>
 * The first dispatch of an asynchronous handler is left unmarked as well: the servlet calls
 * {@code afterConcurrentHandlingStarted} there instead.
 */
final class DispatchCompletionMarkingInterceptor implements HandlerInterceptor {

    public static final String DISPATCH_COMPLETED_ATTRIBUTE =
            DispatchCompletionMarkingInterceptor.class.getName() + ".COMPLETED";

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        if (ex == null) {
            request.setAttribute(DISPATCH_COMPLETED_ATTRIBUTE, Boolean.TRUE);
        }
    }

    public static Boolean getAndClearAttribute(HttpServletRequest request) {
        Boolean attribute = (Boolean) request.getAttribute(DISPATCH_COMPLETED_ATTRIBUTE);
        request.removeAttribute(DISPATCH_COMPLETED_ATTRIBUTE);
        return attribute;
    }
}
