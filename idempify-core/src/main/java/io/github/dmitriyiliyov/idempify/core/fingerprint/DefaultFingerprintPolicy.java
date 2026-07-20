package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.RequestContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DefaultFingerprintPolicy implements FingerprintPolicy {

    @Override
    public String generate(RequestContext context) {
        StringBuilder sb = new StringBuilder()
                .append(context.getPath())
                .append(":")
                .append(context.getMethod())
                .append(":")
                .append(context.getBody());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte [] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error when hashing idempotent request", e);
        }
    }

    @Override
    public boolean compare(String previous, String current) {
        return previous.equals(current);
    }

    @Override
    public void handle(FingerprintMismatchContext context) {
        throw new FingerprintMismatchException(context);
    }
}
