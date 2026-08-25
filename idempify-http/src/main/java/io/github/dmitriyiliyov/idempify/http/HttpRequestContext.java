package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.IOException;

public class HttpRequestContext implements RequestContext {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestContext.class);
    private final HttpServletRequest request;

    public HttpRequestContext(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public RequestType getRequestType() {
        return RequestType.HTTP;
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    /**
     * The path in the form the request was routed by: relative to the application (neither the context path nor
     * the prefix of the {@code DispatcherServlet} is part of it), percent-decoded and stripped of the matrix
     * parameters the container appends. The value feeds the fingerprint, so anything that varies between two
     * deliveries of the same request - a session id in {@code ;jsessionid}, a redeployment under another context
     * path - has to be out of it, or a legitimate retry stops matching what was stored.
     */
    @Override
    public String getPath() {
        return normalize(requestPath().pathWithinApplication());
    }

    /**
     * The parsed path {@code DispatcherServlet} has already cached for this request, parsing it only when nobody
     * did - the entry point is not necessarily an MVC one.
     */
    private RequestPath requestPath() {
        return ServletRequestPathUtils.hasParsedRequestPath(request)
                ? ServletRequestPathUtils.getParsedRequestPath(request)
                : ServletRequestPathUtils.parseAndCache(request);
    }

    private static String normalize(PathContainer path) {
        StringBuilder sb = new StringBuilder();
        for (PathContainer.Element element : path.elements()) {
            sb.append(element instanceof PathContainer.PathSegment segment
                    ? segment.valueToMatch()
                    : element.value());
        }
        return sb.toString();
    }

    @Override
    public byte [] getBodyBytes() {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Error when reading request body", e);
            throw new RuntimeException("Error when reading request body", e);
        }
    }

    @Override
    public String toString() {
        return "HttpRequestContext{" +
                "request=" + request +
                '}';
    }
}
