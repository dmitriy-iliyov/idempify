package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

/**
 * The outcome of one call's attempt to claim a key in the non-blocking branch.
 * <p>
 * The token is the one the call offered, and it is there whether or not the claim was taken; only while
 * {@link #isAcquired()} holds does it open the ways out of the claim.
 */
public interface LockDetail {
    boolean isAcquired();

    UUID getLockToken();
}
