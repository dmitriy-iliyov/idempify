package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.*;

import static io.github.dmitriyiliyov.idempify.http.HttpConstants.SUPPORTED_METHODS;

/**
 * Patterns are stored exactly as the mappings declare them - neither the context path nor the servlet prefix
 * is glued on. The request is matched by its {@link RequestPath#pathWithinApplication()}, which has both of
 * them stripped already, so the two sides live in the same space by construction.
 */
public class DefaultIdempotentHandlerRegistry implements IdempotentHandlerRegistry, SmartInitializingSingleton {

    private final RequestMappingHandlerMapping handlerMapping;
    private volatile Map<MethodKey, HandlerMethod> patternToMethodBinding = Collections.emptyMap();

    public DefaultIdempotentHandlerRegistry(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = Objects.requireNonNull(handlerMapping, "handlerMapping cannot be null");
    }

    @Override
    public void afterSingletonsInstantiated() {
        PathPatternParser parser = patternParser();
        Map<MethodKey, HandlerMethod> binding = new HashMap<>();

        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (!handlerMethod.hasMethodAnnotation(Idempotent.class)) {
                return;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                methods = SUPPORTED_METHODS;
            }

            for (String rawPattern : info.getPatternValues()) {
                PathPattern pattern = parser.parse(rawPattern.isBlank() ? "/" : rawPattern);
                for (RequestMethod method : methods) {
                    if (SUPPORTED_METHODS.contains(method)) {
                        binding.put(new MethodKey(method.name(), pattern), handlerMethod);
                    }
                }
            }
        });
        this.patternToMethodBinding = Map.copyOf(binding);
    }

    private PathPatternParser patternParser() {
        PathPatternParser parser = handlerMapping.getPatternParser();
        return parser != null ? parser : PathPatternParser.defaultInstance;
    }

    @Override
    public HandlerMethod getHandlerMethod(String method, RequestPath requestPath) {
        if (argumentsAreInvalid(method, requestPath)) {
            return null;
        }

        PathContainer path = requestPath.pathWithinApplication();

        return patternToMethodBinding.entrySet().stream()
                .filter(entry -> method.equalsIgnoreCase(entry.getKey().method()))
                .filter(entry -> entry.getKey().pattern().matches(path))
                .min(Comparator.comparing((Map.Entry<MethodKey, HandlerMethod> entry) -> entry.getKey().pattern()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private boolean argumentsAreInvalid(String method, RequestPath requestPath) {
        return method == null || method.isBlank() || requestPath == null;
    }
}
