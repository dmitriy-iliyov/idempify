package io.github.dmitriyiliyov.idempify.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class CachedHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(CachedHttpServletRequestWrapper.class);
    private final byte [] body;

    public CachedHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.error("Error when reading request body", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            public int read() {
                return stream.read();
            }

            public boolean isFinished() {
                return stream.available() == 0;
            }

            public boolean isReady() {
                return true;
            }

            public void setReadListener(ReadListener listener) {}
        };
    }
}
