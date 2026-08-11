package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.RequestPath;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.Objects;

public class DefaultIdempotentRequestMatcher implements IdempotentRequestMatcher {

    private final IdempotentHandlerRegistry handlerRegistry;
    private final OperationMetadataResolver metadataResolver;

    public DefaultIdempotentRequestMatcher(IdempotentHandlerRegistry handlerRegistry,
                                           OperationMetadataResolver metadataResolver) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry cannot be null");
        this.metadataResolver = Objects.requireNonNull(metadataResolver, "metadataResolver cannot be null");
    }

    @Override
    public OperationMetadata match(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        RequestPath requestPath = ServletRequestPathUtils.parseAndCache(request);

        HandlerMethod handlerMethod = handlerRegistry.getHandlerMethod(request.getMethod(), requestPath);
        if (handlerMethod == null) {
            return null;
        }

        return metadataResolver.resolve(handlerMethod.getMethod(), handlerMethod.getBeanType());
    }
}
