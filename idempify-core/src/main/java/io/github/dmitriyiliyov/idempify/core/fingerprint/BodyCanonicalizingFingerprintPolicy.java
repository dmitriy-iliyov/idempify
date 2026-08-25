package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class BodyCanonicalizingFingerprintPolicy extends AbstractFingerprintPolicy {

    private final BodyCanonicalizer bodyCanonicalizer;

    public BodyCanonicalizingFingerprintPolicy(EmptyBodyFallback fallback, BodyCanonicalizer bodyCanonicalizer) {
        super(fallback);
        this.bodyCanonicalizer = Objects.requireNonNull(bodyCanonicalizer, "bodyCanonicalizer cannot be null");
    }

    @Override
    public String generate(RequestContext context) {
        StringBuilder sb = FingerprintUtils.toStringBuilderWithoutBody(context);

        byte [] bytes = getRequestBodyWithFallback(context);
        String canonicalizedBody;
        if (bytes.length > 0) {
            canonicalizedBody = bodyCanonicalizer.canonicalize(bytes);
        } else {
            canonicalizedBody = "";
        }

        sb.append("|")
                .append(canonicalizedBody.length())
                .append("|")
                .append(canonicalizedBody);

        return hash(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
