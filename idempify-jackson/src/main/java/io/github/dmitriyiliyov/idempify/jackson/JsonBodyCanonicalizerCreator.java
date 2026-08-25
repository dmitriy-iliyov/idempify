package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;

import java.util.Objects;

public class JsonBodyCanonicalizerCreator implements BodyCanonicalizerCreator {

    private final ObjectMapper objectMapper;

    public JsonBodyCanonicalizerCreator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public BodyCanonicalizer create(BodyCanonicalizerConfig config) {
        return new JsonBodyCanonicalizer(config, objectMapper);
    }

    @Override
    public BodyFormat getFormat() {
        return BodyFormat.JSON;
    }
}
