package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.RequestContext;
import io.github.dmitriyiliyov.idempify.core.RequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HttpRequestContextProvider implements RequestContextProvider {

    @Override
    public RequestContext getContext() {
        ServletRequestAttributes requestAttributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        if (requestAttributes == null) {
            throw new IllegalStateException("requestAttributes is null");
        }
        HttpServletRequest request = requestAttributes.getRequest();
        return new HttpRequestContext(request);
    }
}
