package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.aop.Idempotent;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DefaultIdempotentEndpointRegistry implements IdempotentEndpointRegistry, ApplicationListener<ContextRefreshedEvent> {

    private final RequestMappingHandlerMapping handlerMapping;
    private final Set<String> patterns;

    public DefaultIdempotentEndpointRegistry(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = Objects.requireNonNull(handlerMapping, "handlerMapping cannot be null");
        this.patterns = new HashSet<>();
    }

    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        handlerMapping.getHandlerMethods()
                .forEach((info, method) -> {
                    if (method.hasMethodAnnotation(Idempotent.class)) {
                        patterns.addAll(info.getPatternValues());
                    }
                });
    }

    @Override
    public Set<String> getPatterns() {
        return patterns;
    }
}
