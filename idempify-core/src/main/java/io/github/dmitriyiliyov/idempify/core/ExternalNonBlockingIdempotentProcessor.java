package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs {@link ProcessorType#EXTERNAL_NON_BLOCKING}: the business method runs outside any transaction of the
 * library, so its effect stays whatever happens to the record, and the outcome is written after it returns -
 * the result onto the record, or a failure released or kept on it.
 * <p>
 * An outcome that could not be written keeps the claim: the call fails with {@link ResultNotRecordedException},
 * and the key stays held until the lease runs out instead of being freed for a repeat that would run the
 * operation again at once.
 * <p>
 * A call already inside a transaction is refused before anything is claimed.
 */
public class ExternalNonBlockingIdempotentProcessor extends AbstractNonBlockingIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExternalNonBlockingIdempotentProcessor.class);

    public ExternalNonBlockingIdempotentProcessor(NonBlockingOperationManager manager,
                                                  OperationStateChannel stateChannel,
                                                  IdempotencyEventListener eventListener) {
        super(manager, stateChannel, eventListener);
    }

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        isTransactionExists();

        NonBlockingOperationDetail detail = null;
        boolean shouldRecordResponse = true;
        boolean shouldUnlock = true;

        LockDetail lockDetail = tryLock(context, metadata);
        IdempotentProcessorUtils.CallProgress progress = new IdempotentProcessorUtils.CallProgress();
        try {
            detail = fetchAndValidate(context, metadata, lockDetail);

            if (OperationStatus.IN_PROCESS.equals(detail.getStatus())) {
                Object result;

                try {
                    result = context.getCallback().call();
                } catch (Throwable t) {
                    log.debug("Operation (idempotencyKey={}) ended exceptionally", context.getIdempotencyKey(), t);
                    try {
                        shouldRecordResponse = guardBusinessFailure(
                                t, context, metadata, lockDetail.getLockToken(), progress
                        );
                    } catch (Throwable th) {
                        shouldRecordResponse = false;
                        shouldUnlock = false;
                        throw th;
                    }
                    throw t;
                }

                try {
                    detail = manager.complete(detail, result, metadata.getTtl());
                    eventListener.onSuccess();
                } catch (Throwable t) {
                    shouldRecordResponse = false;
                    shouldUnlock = false;
                    if (t instanceof Exception e) {
                        throw new ResultNotRecordedException(context.getIdempotencyKey(), e);
                    }
                    throw t;
                }
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
            if (lockDetail.isAcquired() && shouldUnlock) {
                unlock(context, lockDetail, progress);
            }
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.EXTERNAL_NON_BLOCKING;
    }
}
