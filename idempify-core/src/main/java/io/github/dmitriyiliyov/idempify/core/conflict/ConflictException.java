package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.IdempifyException;

/**
 * The common type of what settling a conflict throws: a call refused while another one holds the key
 * ({@link OperationInProgressException}), a wait for it given up ({@link WaitAbortedException}), or a
 * {@link ConflictHandler} that broke its contract ({@link ConflictHandlerResultException}). Each already says what
 * happened, so a processor throws it on as it is rather than as a failure of its own.
 */
public class ConflictException extends IdempifyException {
    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
