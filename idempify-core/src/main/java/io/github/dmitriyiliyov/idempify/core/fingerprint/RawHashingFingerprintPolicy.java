package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

import java.nio.charset.StandardCharsets;

public class RawHashingFingerprintPolicy extends AbstractFingerprintPolicy {

    public RawHashingFingerprintPolicy(EmptyBodyFallback fallback) {
        super(fallback);
    }

    @Override
    public String generate(RequestContext context) {
        StringBuilder sb = FingerprintUtils.toStringBuilderWithoutBody(context);

        byte [] bodyBytes = getRequestBodyWithFallback(context);

        sb.append("|")
                .append(bodyBytes.length)
                .append("|");

        byte [] requestMetadataBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte [] bytes = new byte[requestMetadataBytes.length + bodyBytes.length];

        System.arraycopy(requestMetadataBytes, 0, bytes, 0, requestMetadataBytes.length);
        System.arraycopy(bodyBytes, 0, bytes, requestMetadataBytes.length, bodyBytes.length);

        return hash(bytes);
    }
}
