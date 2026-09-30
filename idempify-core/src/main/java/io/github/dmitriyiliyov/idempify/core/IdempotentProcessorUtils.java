package io.github.dmitriyiliyov.idempify.core;

import org.springframework.transaction.TransactionStatus;

public final class IdempotentProcessorUtils {

    private IdempotentProcessorUtils() {}

    public static void transactionStatusCheck(TransactionStatus status) {
        if (!status.isNewTransaction()) {
            throw new IllegalStateException("""
                    @Idempotent must own its transaction: it is meant for an entry point, \
                    not for a method already running inside a transaction""");
        }
    }

    public static RuntimeException wrapThrowable(Throwable t, String message) {
        if (t instanceof RuntimeException re) {
            return re;
        } else {
            return new IdempotentProcessingException(message, t);
        }
    }
}
