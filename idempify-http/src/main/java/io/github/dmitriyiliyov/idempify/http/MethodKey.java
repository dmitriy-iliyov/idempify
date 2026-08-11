package io.github.dmitriyiliyov.idempify.http;

import org.springframework.web.util.pattern.PathPattern;

public record MethodKey(
        String method,
        PathPattern pattern
) { }
