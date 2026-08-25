package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Indexes the registered {@link BodyCanonicalizerCreator}s by the format each one handles, and hands the
 * matching one the config of the call site being resolved.
 * <p>
 * Completeness is not checked here: which formats an application needs depends on what its endpoints receive,
 * so a missing creator is only an error once a call site actually asks for that format.
 */
public class DefaultBodyCanonicalizerProvider implements BodyCanonicalizerProvider {

    private final Map<BodyFormat, BodyCanonicalizerCreator> creators;

    public DefaultBodyCanonicalizerProvider(List<BodyCanonicalizerCreator> creators) {
        Objects.requireNonNull(creators, "creators cannot be null");
        if (creators.isEmpty()) {
            throw new IllegalStateException(
                    "At least one BodyCanonicalizerCreator must be registered, but none was found"
            );
        }

        this.creators = new EnumMap<>(BodyFormat.class);
        for (BodyCanonicalizerCreator creator : creators) {
            BodyCanonicalizerCreator registered = this.creators.putIfAbsent(creator.getFormat(), creator);
            if (registered != null) {
                throw new IllegalStateException(
                        "Exactly one BodyCanonicalizerCreator must be registered for format %s, but both %s and %s are"
                                .formatted(creator.getFormat(), registered.getClass().getName(), creator.getClass().getName())
                );
            }
        }
    }

    @Override
    public BodyCanonicalizer provide(BodyCanonicalizerConfig config) {
        Objects.requireNonNull(config, "config cannot be null");

        BodyCanonicalizerCreator creator = creators.get(config.getFormat());
        if (creator == null) {
            throw new IllegalStateException(
                    "BodyCanonicalizerCreator not found for %s format, registered formats are %s"
                            .formatted(config.getFormat(), creators.keySet())
            );
        }
        return creator.create(config);
    }
}
