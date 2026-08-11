package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HttpRequestContextProvider implements RequestContextProvider {

    @Override
    public RequestContext getContext() {
        ServletRequestAttributes requestAttributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        if (requestAttributes == null) {
            throw new IllegalStateException("No active HTTP request bound to the current thread");
        }
        HttpServletRequest request = requestAttributes.getRequest();
        return new HttpRequestContext(request);
    }
}
