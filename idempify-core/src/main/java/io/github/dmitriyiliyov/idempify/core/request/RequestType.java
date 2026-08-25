package io.github.dmitriyiliyov.idempify.core.request;

/**
 * The transport a request arrived over.
 * <p>
 * Selects the transport-specific pieces of the pipeline - chiefly which {@link KeyExtractor} reads the
 * idempotency key, since where the key lives differs between transports.
 */
public enum RequestType {

    /**
     * An HTTP request; the key is read from a header. Implemented by {@code idempify-http}.
     */
    HTTP,

    /**
     * A gRPC call; the key would be read from request metadata. Declared but not implemented - there is no
     * gRPC module yet.
     */
    GRPC
}
