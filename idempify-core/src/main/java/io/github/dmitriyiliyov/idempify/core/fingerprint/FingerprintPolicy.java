package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.request.RequestContext;

/**
 * Reduces a request to the short string that says what was asked, so that the same key arriving with a
 * different request can be told from a genuine retry - and decides what happens when it is.
 * <p>
 * All three methods belong together on purpose: how much of a body two requests may differ in and still count
 * as the same one is the same decision as how the difference is punished, and splitting them would let a call
 * site hash one way and react another.
 * <p>
 * {@link #generate} must be <strong>deterministic across processes</strong>: the value it returns is compared
 * against one produced earlier, possibly on another machine.
 *
 * @see BodyHandleStrategy
 */
public interface FingerprintPolicy {

    /**
     * Returns the fingerprint of this request. Never {@code null} or blank - a caller that has one may store
     * and compare it as is.
     */
    String generate(RequestContext context);

    /**
     * Returns whether the two fingerprints describe the same request. A {@code null} previous value means
     * nothing was stored to compare against, which is not a match.
     */
    boolean match(String previous, String current);

    /**
     * Reacts to a mismatch that {@link #match} reported. Throwing is the normal implementation: the same key
     * carrying a different request is a client error, not something to answer with someone else's result.
     */
    void handle(FingerprintMismatchContext context);
}
