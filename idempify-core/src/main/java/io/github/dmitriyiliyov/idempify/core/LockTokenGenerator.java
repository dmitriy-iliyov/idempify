package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * Issues the token a non-blocking claim is taken under. Every way out of the claim is conditional on that
 * token, so no two claims may ever be issued the same one.
 */
public interface LockTokenGenerator {
    UUID generate();
}
