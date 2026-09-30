package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class NonBlockingOperationDeserializer implements OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> {

    private final ResultDeserializer resultDeserializer;
    private final ResponseDeserializer responseDeserializer;
    private final Map<String, Class<?>> exceptionClassNameCache;

    public NonBlockingOperationDeserializer(ResultDeserializer resultDeserializer,
                                            ResponseDeserializer responseDeserializer) {
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer cannot be null");
        this.exceptionClassNameCache = new ConcurrentHashMap<>();
    }

    @Override
    public NonBlockingOperation deserialize(RawNonBlockingOperation rawOperation, ResultType resultType) {
        if (rawOperation == null) {
            return null;
        }

        Object result;
        if (OperationStatus.EXCEPTIONALLY_PROCESSED.equals(rawOperation.status())) {
            result = resolveException(rawOperation.exceptionClassName(), rawOperation.result());
        } else {
            result = rawOperation.result() == null || resultType == null
                    ? null
                    : resultDeserializer.deserialize(rawOperation.result(), resultType);
        }
        return new NonBlockingOperation(
                rawOperation.idempotencyKey(),
                rawOperation.status(),
                rawOperation.isFirstAttempt(),
                result,
                resultType,
                rawOperation.response() == null ? null : responseDeserializer.deserialize(rawOperation.response()),
                rawOperation.fingerprint(),
                rawOperation.remainingMillis(),
                rawOperation.createdAt(),
                rawOperation.lockToken(),
                rawOperation.remainingLockMillis()
        );
    }

    private Object resolveException(String exceptionClassName, Object exceptionMessage) {
        Class<?> exceptionClass = exceptionClassNameCache.get(exceptionClassName);
        if (exceptionClass == null) {
            try {
                exceptionClass = Class.forName(exceptionClassName);
                exceptionClassNameCache.put(exceptionClassName, exceptionClass);
            } catch (ClassNotFoundException cce) {
                throw new IllegalStateException();
            }
        }
        String message = (String) exceptionMessage;
        try {
            return exceptionClass.getDeclaredConstructor(String.class).newInstance(message);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
