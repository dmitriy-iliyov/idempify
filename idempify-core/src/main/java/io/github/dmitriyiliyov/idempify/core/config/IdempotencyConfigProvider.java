package io.github.dmitriyiliyov.idempify.core.config;

/**
 * The single abstraction over the {@code idempify.*} properties: whatever holds them must be able to hand the
 * core an {@link IdempotencyConfig}.
 * <p>
 * Properties mirror the shape of the YAML rather than the shape of the core, and this method is the one place
 * the two trees meet. That leaves the binding surface free to follow property conventions while the core
 * keeps a single config type to layer.
 */
public interface IdempotencyConfigProvider {

    /**
     * Converts the held properties into the config the core layers under everything else.
     * <p>
     * The result is a <em>complete</em> config - {@link IdempotencyConfig#notEmpty()} holds for it. Being the
     * least specific layer, it is the one every more specific source falls back on, so it owes an answer for
     * every setting: a feature the application left out is handed over as a disabled section, never as a
     * missing one. A source unable to decide a setting has nothing below it to ask.
     *
     * @return the global config described by these properties.
     */
    IdempotencyConfig provide();
}
