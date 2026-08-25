package io.github.dmitriyiliyov.idempify.core;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultOperationMetadataCache implements OperationMetadataCache {

    private final Map<Method, OperationMetadata> storage = new ConcurrentHashMap<>();

    @Override
    public OperationMetadata get(Method method) {
        return storage.get(method);
    }

    @Override
    public void put(Method method, OperationMetadata metadata) {
        storage.put(method, metadata);
    }
}
