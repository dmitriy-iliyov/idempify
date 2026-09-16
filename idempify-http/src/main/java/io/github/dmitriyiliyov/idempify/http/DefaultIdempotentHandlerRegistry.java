package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.Idempotent;
import io.github.dmitriyiliyov.idempify.core.config.IdempotentEntry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.*;

import static io.github.dmitriyiliyov.idempify.http.HttpConstants.SUPPORTED_METHODS;

/**
 * Patterns are stored exactly as the mappings declare them - neither the context path nor the servlet prefix
 * is glued on. The request is matched by its {@link RequestPath#pathWithinApplication()}, which has both of
 * them stripped already, so the two sides live in the same space by construction.
 * <p>
 * The same pass asks whether an entry intercepts each endpoint it indexes and refuses the start-up when none
 * does. The check belongs here rather than in a checker of its own for two reasons: the set being checked has
 * to be the set that was indexed, and a pass of its own would say what an endpoint is a second time; and the
 * answer is only readable once every singleton is proxied, which is where this pass already stands. A context
 * holding no entry at all is left to the check in the core, which says so once instead of once per endpoint.
 */
public class DefaultIdempotentHandlerRegistry implements IdempotentHandlerRegistry, SmartInitializingSingleton {

    private final RequestMappingHandlerMapping handlerMapping;
    private final List<IdempotentEntry> entries;
    private volatile Map<MethodKey, HandlerMethod> patternToMethodBinding = Collections.emptyMap();

    public DefaultIdempotentHandlerRegistry(RequestMappingHandlerMapping handlerMapping,
                                            List<IdempotentEntry> entries) {
        this.handlerMapping = Objects.requireNonNull(handlerMapping, "handlerMapping cannot be null");
        this.entries = Objects.requireNonNull(entries, "entries cannot be null");
    }

    @Override
    public void afterSingletonsInstantiated() {
        PathPatternParser parser = patternParser();
        Map<MethodKey, HandlerMethod> binding = new HashMap<>();

        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (!handlerMethod.hasMethodAnnotation(Idempotent.class)) {
                return;
            }
            verifyIntercepted(info, handlerMethod);
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                methods = SUPPORTED_METHODS;
            }

            for (String rawPattern : info.getPatternValues()) {
                PathPattern pattern = parser.parse(rawPattern.isBlank() ? "/" : rawPattern);
                for (RequestMethod method : methods) {
                    verifyRequestMethod(info, method);
                    binding.put(new MethodKey(method.name(), pattern), handlerMethod);
                }
            }
        });
        this.patternToMethodBinding = Map.copyOf(binding);
    }

    private PathPatternParser patternParser() {
        PathPatternParser parser = handlerMapping.getPatternParser();
        return parser != null ? parser : PathPatternParser.defaultInstance;
    }

    private void verifyIntercepted(RequestMappingInfo info, HandlerMethod handlerMethod) {
        if (entries.isEmpty()) {
            return;
        }

        HandlerMethod resolved = handlerMethod.createWithResolvedBean();
        for (IdempotentEntry entry : entries) {
            if (entry.intercepts(resolved.getBean(), resolved.getMethod())) {
                return;
            }
        }

        throw new IllegalStateException("""
                Endpoint %s carries @Idempotent, but no entry intercepts %s, so the call site runs \
                unprotected: check that aspect proxying is on (spring.aop.auto) and that the handler \
                method is public and not final""".formatted(info, resolved.getMethod()));
    }

    private void verifyRequestMethod(RequestMappingInfo info, RequestMethod method) {
        if (!SUPPORTED_METHODS.contains(method)) {
            throw new IllegalStateException(
                    "Endpoint %s carries @Idempotent, but its HTTP method '%s' is not supported".formatted(info, method)
            );
        }
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
