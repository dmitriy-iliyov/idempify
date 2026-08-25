package io.github.dmitriyiliyov.idempify.core.fingerprint;

/**
 * Reduces a raw request body to a stable string, so that semantically equal bodies fingerprint the same way
 * regardless of byte-level differences - JSON key order, insignificant whitespace, and the like.
 * <p>
 * An implementation is tied to one body format and receives nothing but the bytes, so it has to recognise
 * that format on its own. Formats that cannot be read without an external schema - protobuf needs a
 * {@code Descriptor} to tell a field number from its value - are out of reach of this signature; supporting
 * them would mean handing the canonicalizer the parsed message rather than the bytes.
 * <p>
 * The result is compared against a value produced earlier, on another machine and possibly by another
 * process, so an implementation must be <strong>deterministic</strong>: identical input bytes must always
 * yield an identical string. In particular it must not depend on the default locale, on the iteration order
 * of a hash-based collection, on the current time, or on any state outside the body itself. Changing the
 * rules of an existing implementation invalidates every fingerprint already stored.
 */
public interface BodyCanonicalizer {

    /**
     * Returns the canonical representation of the given raw body. Never {@code null}.
     *
     * @throws IllegalArgumentException if the body is not readable as the format this canonicalizer handles
     */
    String canonicalize(byte [] body);
}
