package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

public abstract class AbstractOperationResponseCache implements OperationResponseCache {

    @Override
    public Object findByIdempotencyKey(UUID idempotencyKey) {
        return null;
    }
}
