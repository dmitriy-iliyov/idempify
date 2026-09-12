package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public final class FilterUtils {

    private static final Logger log = LoggerFactory.getLogger(FilterUtils.class);

    private FilterUtils() { }

    /**
     * Whether the response just written is one to keep. A flag the configuration left undecided reads as
     * "do not keep": every layer down to {@link ResponseConfig#defaults()} may leave it {@code null}, and an
     * undecided flag is not permission.
     *
     * @param responseConfig the resolved settings of the call site, or {@code null} when it has none.
     * @param status         the status the handler answered with.
     */
    public static boolean shouldKeep(ResponseConfig responseConfig, int status) {
        if (responseConfig == null) {
            return false;
        }

        if (is4xx(status)) {
            return Boolean.TRUE.equals(responseConfig.shouldCache4xx());
        }

        if (is5xx(status)) {
            return Boolean.TRUE.equals(responseConfig.shouldCache5xx());
        }

        return true;
    }

    public static boolean is4xx(int status) {
        return status >= 400 && status < 500;
    }

    public static boolean is5xx(int status) {
        return status >= 500 && status < 600;
    }

    public static void writeToServletResponse(UUID idempotencyKey,
                                              HttpServletResponse response,
                                              Response responseView) throws IOException {
        response.setStatus(responseView.getStatus());

        String contentType = responseView.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            response.setContentType(contentType);
        } else {
            log.warn("Operation (idempotencyKey={}) response contentType is null or blank", idempotencyKey);
        }

        responseView.getHeaders().forEach(response::setHeader);

        byte [] body = responseView.getBody();
        int length = body == null ? 0 : body.length;
        response.setContentLength(length);

        if (length > 0) {
            response.getOutputStream().write(body);
        }
    }
}
