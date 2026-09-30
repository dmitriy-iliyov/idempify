package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

public class DefaultNonBlockingOperationDetail extends DefaultOperationDetail implements NonBlockingOperationDetail {

    private final boolean isAcquired;
    private final UUID lockToken;

    public DefaultNonBlockingOperationDetail(boolean isAcquired,
                                             UUID lockToken,
                                             UUID idempotencyKey,
                                             OperationStatus status,
                                             Object result,
                                             boolean shouldRecordResponse) {
        super(idempotencyKey, status, result, shouldRecordResponse);
        this.isAcquired = isAcquired;
        this.lockToken = lockToken;
    }

    public DefaultNonBlockingOperationDetail(LockDetail lockDetail,
                                             NonBlockingOperation operation,
                                             boolean shouldRecordResponse) {
        this(
                lockDetail.isAcquired(), lockDetail.getLockToken(),
                operation.getIdempotencyKey(), operation.getStatus(), operation.getResult(),
                shouldRecordResponse
        );
    }

    @Override
    public boolean isAcquired() {
        return isAcquired;
    }

    @Override
    public UUID getLockToken() {
        return lockToken;
    }
}
