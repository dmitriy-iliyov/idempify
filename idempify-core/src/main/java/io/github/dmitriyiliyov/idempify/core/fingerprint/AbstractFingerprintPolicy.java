package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public abstract class AbstractFingerprintPolicy implements FingerprintPolicy {

    private static final Logger log = LoggerFactory.getLogger(AbstractFingerprintPolicy.class);
    protected final EmptyBodyFallback fallback;

    protected AbstractFingerprintPolicy(EmptyBodyFallback fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback cannot be null");
    }

    protected byte [] getRequestBodyWithFallback(RequestContext context) {
        byte [] bytes = context.getBodyBytes();
        if (bytes != null && bytes.length > 0) {
            return bytes;
        }

        byte [] fallbackBytes = fallback.fallback(context);
        if (fallbackBytes == null) {
            throw new IllegalStateException(
                    "EmptyBodyFallback %s returned null; it must return bytes to fingerprint, an empty array to leave the body out, or throw"
                            .formatted(fallback.getClass().getName())
            );
        }
        return fallbackBytes;
    }

    protected String hash(byte [] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte [] hash = md.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error when hashing idempotent request", e);
        }
    }

    @Override
    public boolean match(String previous, String current) {
        if (previous == null) {
            return false;
        }
        return previous.equals(current);
    }

    @Override
    public void handle(FingerprintMismatchContext context) {
        log.error("Detected operation fingerprint mismatch; {}", context);
        throw new FingerprintMismatchException(context);
    }
}
