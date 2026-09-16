package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Marks a request in which an exception reached the exception resolvers of {@code DispatcherServlet}, whichever
 * resolver answers it afterwards. It answers nothing itself - {@code null} lets resolution go on - and comes
 * first, so that no resolver can answer before the mark is set.
 * <p>
 * The servlet's own traces are not enough: {@code DispatcherServlet.EXCEPTION_ATTRIBUTE} is set only when the
 * resolver wrote the body itself, and the {@code jakarta.servlet.error.*} attributes of a resolution into a view
 * are cleared as soon as that view is rendered.
 */
final class DispatchFailureMarkingResolver implements HandlerExceptionResolver, Ordered {

    private static final String DISPATCH_FAILED_ATTRIBUTE = DispatchFailureMarkingResolver.class.getName() + ".FAILED";

    @Nullable
    @Override
    public ModelAndView resolveException(@NonNull HttpServletRequest request,
                                         @NonNull HttpServletResponse response,
                                         @Nullable Object handler,
                                         @NonNull Exception ex) {
        request.setAttribute(DISPATCH_FAILED_ATTRIBUTE, Boolean.TRUE);
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public static Boolean getAndClearAttribute(HttpServletRequest request) {
        Boolean attribute = (Boolean) request.getAttribute(DISPATCH_FAILED_ATTRIBUTE);
        request.removeAttribute(DISPATCH_FAILED_ATTRIBUTE);
        return attribute;
    }
}
