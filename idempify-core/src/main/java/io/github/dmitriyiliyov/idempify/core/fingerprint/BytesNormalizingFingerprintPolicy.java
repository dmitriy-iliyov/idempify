package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Fingerprints the body as UTF-8 text normalized to Unicode NFC, so that the same characters written as
 * precomposed or decomposed code points hash the same way.
 * <p>
 * <strong>Text bodies only.</strong> Decoding runs in replacement mode: every byte sequence that is not
 * valid UTF-8 becomes {@code U+FFFD}. Two different binary bodies - an image, a protobuf message, a gzip
 * stream - therefore collapse into the same string and produce the <em>same fingerprint</em>, which defeats
 * the point of fingerprinting. Use {@link RawHashingFingerprintPolicy} for anything that is not text.
 */
public class BytesNormalizingFingerprintPolicy extends AbstractFingerprintPolicy {

    public BytesNormalizingFingerprintPolicy(EmptyBodyFallback fallback) {
        super(fallback);
    }

    @Override
    public String generate(RequestContext context) {
        StringBuilder sb = FingerprintUtils.toStringBuilderWithoutBody(context);

        String text = new String(getRequestBodyWithFallback(context), StandardCharsets.UTF_8);
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

        sb.append("|")
                .append(text.length())
                .append("|")
                .append(text);

        return hash(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
