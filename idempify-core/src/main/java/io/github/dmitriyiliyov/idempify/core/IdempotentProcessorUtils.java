package io.github.dmitriyiliyov.idempify.core;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

public final class IdempotentProcessorUtils {

    private IdempotentProcessorUtils() {}

    public static void validateTransactionStatus(TransactionStatus status) {
        if (!status.isNewTransaction()) {
            throw new ForeignTransactionException("""
                    @Idempotent must own its transaction: it is meant for an entry point, \
                    not for a method already running inside a transaction""");
        }
    }

    public static void validateTransactionPropagation(TransactionTemplate transactionTemplate) {
        int propagation = transactionTemplate.getPropagationBehavior();
        if (propagation != TransactionDefinition.PROPAGATION_REQUIRED
                && propagation != TransactionDefinition.PROPAGATION_REQUIRES_NEW
                && propagation != TransactionDefinition.PROPAGATION_NESTED) {
            throw new IllegalArgumentException(
                    "transactionTemplate must open a transaction of its own, but its propagation was %s".formatted(propagation)
            );
        }
    }

    /**
     * Names what a failure says about the call rather than about the operation: {@link FailureType#MISPLACED}
     * for a {@link MisplacedException}, {@link FailureType#FAILED_BEFORE_RUN} for an {@link EarlyFailureException},
     * {@link FailureType#OUTCOME_NOT_RECORDED} for an {@link OutcomeNotRecordedException} and
     * {@link FailureType#INTERRUPTED} for an {@link InterruptedException} or any other failure thrown while the
     * thread's interrupt flag is set. The checks run in this order, so one failure is named once and a failure of
     * the library keeps its own type on an interrupted thread: an interruption is likeliest exactly when an outcome
     * could not be recorded, and that type alone says the effect may stand without a record of it.
     *
     * @return the type, or {@code null} when the failure is the operation's own.
     */
    public static FailureType libraryFailureTypeOf(Throwable t) {
        if (t instanceof MisplacedException) {
            return FailureType.MISPLACED;
        } else if (t instanceof EarlyFailureException) {
            return FailureType.FAILED_BEFORE_RUN;
        } else if (t instanceof OutcomeNotRecordedException) {
            return FailureType.OUTCOME_NOT_RECORDED;
        } else if (t instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
            return FailureType.INTERRUPTED;
        }
        return null;
    }

    /**
     * What one call has done so far that a later step of it has to know, held in an object of its own because a
     * transaction callback cannot reassign a local variable it captures.
     * <p>
     * Two marks, each set once and never cleared: the operation has started - past that point a failure is no
     * longer one before the business method - and the key has been released by removing the record, after which
     * there is no claim left to give back. One instance belongs to one call on its own thread, so nothing here
     * is shared.
     */
    public static final class CallProgress {

        private boolean operationStarted = false;
        private boolean keyReleased = false;

        public void markOperationStarted() {
            operationStarted = true;
        }

        public boolean isOperationStarted() {
            return operationStarted;
        }

        public void markKeyReleased() {
            keyReleased = true;
        }

        public boolean isKeyReleased() {
            return keyReleased;
        }
    }
}
