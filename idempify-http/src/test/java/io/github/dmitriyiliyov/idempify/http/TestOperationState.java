package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.OperationState;

/**
 * Stands in for whatever core publishes into the channel: http reads nothing from it but whether the response
 * is still to be recorded - the expiry lives on the record instead.
 */
final class TestOperationState implements OperationState {

    private final boolean shouldRecordResponse;

    private TestOperationState(boolean shouldRecordResponse) {
        this.shouldRecordResponse = shouldRecordResponse;
    }

    static TestOperationState of(boolean shouldRecordResponse) {
        return new TestOperationState(shouldRecordResponse);
    }

    @Override
    public boolean shouldRecordResponse() {
        return shouldRecordResponse;
    }
}
