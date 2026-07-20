package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.RequestContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultFingerprintManager implements FingerprintManager {

    private final Map<Class<? extends FingerprintPolicy>, FingerprintPolicy> policies;

    public DefaultFingerprintManager(List<FingerprintPolicy> policies) {
        Objects.requireNonNull(policies, "policies cannot be null");
        this.policies = policies.stream()
                .collect(Collectors.toMap(
                        FingerprintPolicy::getClass,
                        Function.identity()
                ));
    }

    @Override
    public String generate(RequestContext context, Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
        return getPolicy(fingerprintPolicyClass).generate(context);
    }

    @Override
    public boolean compareWith(String previous, String current, Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
        return getPolicy(fingerprintPolicyClass).compare(previous, current);
    }

    @Override
    public void handleMismatch(FingerprintMismatchContext context, Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
        getPolicy(fingerprintPolicyClass).handle(context);
    }

    private FingerprintPolicy getPolicy(Class<? extends FingerprintPolicy> fingerprintPolicyClass) {
        FingerprintPolicy policy = policies.get(fingerprintPolicyClass);
        if (policy == null) {
            throw new IllegalStateException("FingerprintPolicy for class %s not found".formatted(fingerprintPolicyClass));
        }
        return policy;
    }
}
