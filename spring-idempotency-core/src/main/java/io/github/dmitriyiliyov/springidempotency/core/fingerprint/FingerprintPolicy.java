package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;

public interface FingerprintPolicy {
    String generate(RequestContext context);
    boolean compare(String previous, String current);
    void handle(FingerprintMismatchContext context);
}
