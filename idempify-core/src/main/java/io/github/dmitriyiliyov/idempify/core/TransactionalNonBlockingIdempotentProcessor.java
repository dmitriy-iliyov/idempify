package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static io.github.dmitriyiliyov.idempify.core.IdempotentProcessorUtils.CallProgress;
import static io.github.dmitriyiliyov.idempify.core.IdempotentProcessorUtils.validateTransactionPropagation;

/**
 * Runs {@link ProcessorType#TRANSACTIONAL_NON_BLOCKING}: the claim is taken and given back outside any
 * transaction, while the business method and the record of its result run in a transaction the processor
 * opens itself, so the effect and the {@link OperationStatus#PROCESSED} record commit or roll back together.
 * <p>
 * A failure of the business method marks that transaction rollback-only, so its effect is gone before the
 * failure is released or kept, outside it. A failure of the record or of the commit keeps nothing: the call
 * fails with {@link OutcomeNotRecordedException}, the transport is told not to record a response, and the next
 * attempt runs the operation as the first. A transaction that cannot be opened fails the call with
 * {@link EarlyFailureException}: the business method has not run.
 * <p>
 * The template has to open a transaction of its own - {@code REQUIRED}, {@code REQUIRES_NEW} or
 * {@code NESTED}, which the constructor checks; a call already inside a transaction is refused before
 * anything is claimed.
 */
public class TransactionalNonBlockingIdempotentProcessor extends AbstractNonBlockingIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionalNonBlockingIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;

    public TransactionalNonBlockingIdempotentProcessor(NonBlockingOperationManager manager,
                                                       OperationStateChannel stateChannel,
                                                       IdempotencyEventListener eventListener,
                                                       TransactionTemplate transactionTemplate) {
        super(manager, stateChannel, eventListener);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        validateTransactionPropagation(transactionTemplate);
    }

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        isTransactionExists();

        NonBlockingOperationDetail detail = null;
        boolean shouldRecordResponse = true;

        LockDetail lockDetail = tryLock(context, metadata);
        CallProgress progress = new CallProgress();
        try {
            detail = fetchAndValidate(context, metadata, lockDetail);

            if (OperationStatus.IN_PROCESS.equals(detail.getStatus())) {
                ProcessResult processResult;
                try {
                    processResult = transactionalProcess(context, metadata, detail, progress);
                } catch (Throwable t) {
                    shouldRecordResponse = false;
                    if (!(t instanceof Error)) {
                        if (progress.isOperationStarted()) {
                            throw new ResultNotRecordedException(detail.getIdempotencyKey(), t);
                        }
                        throw new EarlyFailureException(detail.getIdempotencyKey(), t);
                    }
                    throw t;
                }

                if (processResult.exception() != null) {
                    shouldRecordResponse = false;
                    shouldRecordResponse = guardBusinessFailure(
                            processResult.exception(), context, metadata, lockDetail.getLockToken(), progress
                    );
                    throw processResult.exception();
                }

                detail = processResult.detail();
                eventListener.onSuccess();
            } else {
                eventListener.onDuplicate();
                if (OperationStatus.EXCEPTIONALLY_PROCESSED.equals(detail.getStatus())) {
                    throw (Throwable) detail.getResult();
                }
            }

            return detail.getResult();
        } catch (Throwable t) {
            if (!isReplayedFailure(detail, t)) {
                log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), t);
            }
            throw t;
        } finally {
            publishState(detail, shouldRecordResponse);
            if (lockDetail.isAcquired()) {
                unlock(context, lockDetail, progress);
            }
        }
    }

    /**
     * Runs the business method and records its result in one transaction. {@code progress} marks the operation
     * started once the transaction is open, so a failure of the transaction itself tells whether it came before
     * the operation - opening the transaction - or after it - recording the result, rolling back or committing.
     */
    private ProcessResult transactionalProcess(OperationContext context,
                                               OperationMetadata metadata,
                                               NonBlockingOperationDetail detail,
                                               CallProgress progress) {
        return transactionTemplate.execute(status -> {
            progress.markOperationStarted();
            NonBlockingOperationDetail localDetail = null;
            Throwable exception = null;
            Object opResult = null;

            try {
                opResult = context.getCallback().call();
            } catch (Throwable t) {
                status.setRollbackOnly();
                exception = t;
                log.debug("Operation (idempotencyKey={}) ended exceptionally", context.getIdempotencyKey(), exception);
            }

            if (exception == null) {
                localDetail = manager.complete(detail, opResult, metadata.getTtl());
            }

            return new ProcessResult(localDetail, exception);
        });
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.TRANSACTIONAL_NON_BLOCKING;
    }

    private record ProcessResult(
            NonBlockingOperationDetail detail,
            Throwable exception
    ) { }
}
