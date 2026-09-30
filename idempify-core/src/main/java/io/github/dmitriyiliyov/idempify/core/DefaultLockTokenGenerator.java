package io.github.dmitriyiliyov.idempify.core;

import java.util.UUID;

public class DefaultLockTokenGenerator implements LockTokenGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
