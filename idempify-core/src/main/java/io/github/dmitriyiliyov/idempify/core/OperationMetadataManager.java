package io.github.dmitriyiliyov.idempify.core;

/**
 * Resolves the effective {@link OperationMetadata} for a call by layering the configuration sources from the
 * most specific to the least: the call site's own {@link RawOperationMetadata}, then the named config it
 * points at, then the global properties. A setting left unspecified by one layer is decided by the next.
 */
public interface OperationMetadataManager {

    /**
     * Resolves metadata from the call site and the global properties alone.
     */
    OperationMetadata merge(RawOperationMetadata metadata);

    /**
     * Resolves metadata with a named config layered between the call site and the global properties.
     *
     * @param metadata   what the call site itself asked for.
     * @param configName the name the config was registered under in the config registry.
     */
    OperationMetadata merge(RawOperationMetadata metadata, String configName);
}
