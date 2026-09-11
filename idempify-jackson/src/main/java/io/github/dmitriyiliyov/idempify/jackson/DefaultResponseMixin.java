package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Names the constructor arguments of {@link io.github.dmitriyiliyov.idempify.core.response.DefaultResponse}
 * for Jackson, which cannot find them on its own: the class carries no Jackson annotation - it lives in the
 * core, which knows nothing about Jackson - and reading the names out of the bytecode depends on a module the
 * application registers rather than this one.
 */
abstract class DefaultResponseMixin {

    @JsonCreator
    DefaultResponseMixin(
            @JsonProperty("status") int status,
            @JsonProperty("body") byte [] body,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("headers") Map<String, String> headers
    ) { }
}
