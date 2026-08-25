package io.github.dmitriyiliyov.idempify.core.fingerprint;

/**
 * The order a {@link BodyCanonicalizer} writes an object's members in.
 * <p>
 * Applies to object members only: the order of array elements is part of the meaning of a body and is never
 * rearranged.
 */
public enum CanonicalizeStrategy {

    /**
     * Sort members by name, comparing by code unit rather than by locale so that the result is the same on
     * every machine - the ordering RFC 8785 (JSON Canonicalization Scheme) prescribes.
     */
    LEXICOGRAPHICAL
}
