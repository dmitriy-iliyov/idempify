package io.github.dmitriyiliyov.idempify.http;

import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Set;

public final class HttpConstants {

    public static final Set<RequestMethod> SUPPORTED_METHODS = Set.of(
            RequestMethod.POST, RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.DELETE
    );

    private HttpConstants() {}
}
