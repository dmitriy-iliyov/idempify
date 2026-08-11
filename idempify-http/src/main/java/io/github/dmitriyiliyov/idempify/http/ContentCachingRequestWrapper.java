package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ContentCachingRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(ContentCachingRequestWrapper.class);
    private final byte [] body;

    public ContentCachingRequestWrapper(HttpServletRequest request) {
        super(request);
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Error when reading request body", e);
            throw new RuntimeException("Error when reading request body", e);
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            @Override
            public int read() {
                return stream.read();
            }

            @Override
            public boolean isFinished() {
                return stream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {}
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
    }

    private Charset charset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            log.warn("Request declares an unusable character encoding '{}', reading its body as UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
    }
}
