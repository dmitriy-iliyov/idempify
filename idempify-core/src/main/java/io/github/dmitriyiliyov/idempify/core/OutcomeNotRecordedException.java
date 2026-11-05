package io.github.dmitriyiliyov.idempify.core;

/**
 * Thrown when the business method ran but what it came to could not be written: either a result it returned
 * ({@link ResultNotRecordedException}) or a failure it threw ({@link FailureNotRecordedException}). The cause is
 * the failure of the store or of the commit. A transactional processor leaves the key free for the next attempt,
 * while {@link ProcessorType#EXTERNAL_NON_BLOCKING}, whose effect stays, keeps it claimed until the lease runs out.
 */
public abstract class OutcomeNotRecordedException extends IdempotentProcessingException {

    protected OutcomeNotRecordedException(String message, Throwable cause) {
        super(message, cause);
    }
}
