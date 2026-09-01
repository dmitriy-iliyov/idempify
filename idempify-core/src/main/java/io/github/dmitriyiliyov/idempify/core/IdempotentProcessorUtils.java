package io.github.dmitriyiliyov.idempify.core;

public final class IdempotentProcessorUtils {

    private IdempotentProcessorUtils() {}

    public static Object getResult(ExternalOperationCallback operationCallback) {
        try {
            return operationCallback.call();
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new IdempotentProcessingException("Surrounded method throws", t);
        }
    }
}
