package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseProvidePolicy;
import io.github.dmitriyiliyov.idempify.core.response.ResponseProvider;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Objects;

public class HttpResponseProvider implements ResponseProvider<ContentCachingResponseWrapper> {

    private final ResponseProvidePolicy policy;

    public HttpResponseProvider(ResponseProvidePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
    }

    @Override
    public Response provide(ContentCachingResponseWrapper src) {
        return new DefaultResponse(
                src.getStatus(),
                src.getContentAsByteArray(),
                src.getContentType(),
                HttpResponseProviderUtils.filterHeaders(policy, src)
        );
    }
}
