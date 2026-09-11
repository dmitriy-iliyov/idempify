package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.OperationState;

/**
 * Stands in for whatever core publishes into the channel: http reads nothing from it but whether the answer
 * was replayed - the expiry lives on the record instead.
 */
final class TestOperationState implements OperationState {

    private final boolean replayed;

    private TestOperationState(boolean replayed) {
        this.replayed = replayed;
    }

    static TestOperationState of(boolean replayed) {
        return new TestOperationState(replayed);
    }

    @Override
    public boolean replayed() {
        return replayed;
    }
}
