package io.github.dmitriyiliyov.idempify.core.fingerprint;

import io.github.dmitriyiliyov.idempify.core.IdempifyException;

/**
 * The common type of what the fingerprint check throws: a request with no body to fingerprint
 * ({@link EmptyRequestBodyException}), a call with no fingerprint to compare ({@link EmptyFingerprintException}),
 * or a repeat whose request differs from the stored one ({@link FingerprintMismatchException}). Each already says
 * what happened, so a processor throws it on as it is rather than as a failure of its own.
 */
public class FingerprintException extends IdempifyException {
    public FingerprintException(String message) {
        super(message);
    }
}
