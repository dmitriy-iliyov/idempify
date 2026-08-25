package io.github.dmitriyiliyov.idempify.core;

/**
 * Declares whether a unit of work may run inside the caller's ambient transaction, or must
 * instead run only after that transaction has already been closed.
 *
 * @deprecated nothing reads the answer: the only caller was {@link OperationPropagation}, which is going away
 * with it. The name says the opposite of what it means, too - it answers "may join", not "requires".
 */
@Deprecated(since = "0.0.1", forRemoval = true)
public interface TransactionAffinity {
    /**
     * Returns {@code true} if it is safe to run inside the caller's ambient transaction,
     * {@code false} if the caller should close it first and run this outside of one.
     */
    boolean requiresTransaction();
}
