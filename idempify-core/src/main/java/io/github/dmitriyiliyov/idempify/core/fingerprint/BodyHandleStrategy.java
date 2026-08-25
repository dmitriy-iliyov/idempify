package io.github.dmitriyiliyov.idempify.core.fingerprint;

/**
 * How much the request body is processed before it is hashed into a fingerprint.
 * <p>
 * The order below is one of increasing tolerance: each strategy calls equal some bodies that the previous one
 * would have told apart. Tolerance is not free - every rule that makes two bodies equal is a rule that can
 * make two <em>different</em> requests replay the same result - so the cheapest strategy that accepts your
 * clients' retries is the right one.
 */
public enum BodyHandleStrategy {

    /**
     * Hash the body bytes as they arrived. Any difference in whitespace, key order or encoding yields a
     * different fingerprint.
     * <p>
     * Enough for the usual case, where a retrying client resends the very same buffer, and the only strategy
     * safe for bodies that are not text.
     *
     * @see RawHashingFingerprintPolicy
     */
    RAW_BYTES_HASH,

    /**
     * Decode the body as UTF-8 and normalize it to Unicode NFC before hashing, so that the same characters
     * written as precomposed or decomposed code points agree. Text bodies only.
     *
     * @see BytesNormalizingFingerprintPolicy
     */
    NORMALIZED_BYTES_HASH,

    /**
     * Parse the body and hash a canonical rendering of it, so that formatting and member order stop
     * mattering. The most tolerant and the most expensive, and the only one that can restrict the fingerprint
     * to selected fields.
     *
     * @see BodyCanonicalizingFingerprintPolicy
     * @see BodyCanonicalizer
     */
    CANONICALIZED_BODY_HASH
}
