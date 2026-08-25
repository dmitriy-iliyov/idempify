package io.github.dmitriyiliyov.idempify.core.fingerprint;

/**
 * The wire format of a request body, naming which {@link BodyCanonicalizerCreator} can parse it.
 * <p>
 * A constant here is a declaration of intent, not a guarantee: a format is only usable if some module on the
 * classpath contributes a creator for it. Today {@code idempify-jackson} contributes one, for {@link #JSON}.
 */
public enum BodyFormat {
    JSON,
    XML,
    PROTOBUF
}
