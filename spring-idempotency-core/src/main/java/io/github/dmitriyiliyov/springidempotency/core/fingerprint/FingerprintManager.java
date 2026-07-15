package io.github.dmitriyiliyov.springidempotency.core.fingerprint;

import io.github.dmitriyiliyov.springidempotency.core.RequestContext;

public interface FingerprintManager {
    String generate(RequestContext context,
                    Class<? extends FingerprintPolicy> fingerprintPolicyClass);

    boolean compareWith(String previous, String current,
                        Class<? extends FingerprintPolicy> fingerprintPolicyClass);

    void handleMismatch(FingerprintMismatchContext context,
                        Class<? extends FingerprintPolicy> fingerprintPolicyClass);
}
