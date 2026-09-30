package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NonBlockingIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(NonBlockingIdempotentProcessor.class);
    private final NonBlockingOperationManager manager;
    private final OperationStateChannel stateChannel;
    private final IdempotencyEventListener eventListener;

    public NonBlockingIdempotentProcessor(NonBlockingOperationManager manager,
                                          OperationStateChannel stateChannel,
                                          IdempotencyEventListener eventListener) {
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
        this.stateChannel = Objects.requireNonNull(stateChannel, "stateChannel cannot be null");
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
    }

    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("""
                    @Idempotent with %s processor must not run inside a transaction: its record lives apart from the \
                    business effect, so it is meant for an entry point, not for a method already running inside one"""
                    .formatted(getType()));
        }

        NonBlockingOperationDetail detail = null;
        boolean shouldGuard = false;
        boolean shouldRecordResponse = true;

        LockDetail lockDetail = tryLock(context, metadata);
        try {
            detail = manager.fetchAndValidate(context, metadata, lockDetail);

            if (OperationStatus.IN_PROCESS.equals(detail.getStatus())) {
                shouldGuard = true;
                Object result = context.getCallback().call();
                try {
                    detail = manager.complete(detail, result, metadata.getTtl());
                    eventListener.onSuccess();
                } catch (Throwable t) {
                    shouldGuard = false;
                    shouldRecordResponse = false;
                    throw IdempotentProcessorUtils.wrapThrowable(
                            t,
                            "Error when completing operation (idempotencyKey=%s)".formatted(context.getIdempotencyKey())
                    );
                }
            } else {
                eventListener.onDuplicate();
                if (OperationStatus.EXCEPTIONALLY_PROCESSED.equals(detail.getStatus())) {
                    throw (Throwable) detail.getResult();
                }
            }

            return detail.getResult();
        } catch (Throwable t) {
            if (shouldGuard) {
                log.debug("Operation (idempotencyKey={}) ended exceptionally", context.getIdempotencyKey(), t);
                shouldRecordResponse = false;
                shouldRecordResponse = guardException(t, context, metadata, lockDetail.getLockToken());
            } else if (!isReplayedFailure(detail, t)) {
                log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), t);
            }
            throw t;
        } finally {
            if (lockDetail.isAcquired()) {
                publishState(detail, shouldRecordResponse);
                manager.unlock(context.getIdempotencyKey(), lockDetail.getLockToken());
            }
        }
    }

    private LockDetail tryLock(OperationContext context, OperationMetadata metadata) {
        try {
            return manager.tryLock(context, metadata);
        } catch (Throwable t) {
            log.error("Error when claiming operation (idempotencyKey={})", context.getIdempotencyKey(), t);
            throw IdempotentProcessorUtils.wrapThrowable(
                    t,
                    "Operation (idempotencyKey=%s) could not be claimed".formatted(context.getIdempotencyKey())
            );
        }
    }

    private boolean guardException(Throwable t,
                                   OperationContext context,
                                   OperationMetadata metadata,
                                   UUID lockToken) {
        List<Class<? extends Throwable>> releaseForList = metadata.getResponseConfig().getReleaseFor();
        try {
            boolean isExceptionReleasable = releaseForLoop(t, releaseForList, () -> {
                manager.rollback(context.getIdempotencyKey(), lockToken);
                eventListener.onException();
            });

            if (!isExceptionReleasable) {
                manager.completeExceptionally(context, t, metadata.getTtl(), lockToken);
                eventListener.onExceptionallySuccess();
            }

            return !isExceptionReleasable;
        } catch (Throwable localT) {
            log.error("Error when releasing or recording a failed operation (idempotencyKey={})", context.getIdempotencyKey(), localT);
            eventListener.onException();
            throw IdempotentProcessorUtils.wrapThrowable(
                    localT,
                    "Operation (idempotencyKey=%s) could not be released or recorded after it failed".formatted(context.getIdempotencyKey())
            );
        }
    }

    private boolean releaseForLoop(Throwable t,
                                   List<Class<? extends Throwable>> releaseForList,
                                   Runnable work) {
        for (Class<? extends Throwable> c : releaseForList) {
            if (c.isInstance(t)) {
                work.run();
                return true;
            }
        }
        return false;
    }

    private boolean isReplayedFailure(NonBlockingOperationDetail detail, Throwable t) {
        return detail != null
                && OperationStatus.EXCEPTIONALLY_PROCESSED.equals(detail.getStatus())
                && detail.getResult() == t;
    }

    private void publishState(NonBlockingOperationDetail detail, boolean shouldRecordResponse) {
        final boolean recordResponse = shouldRecordResponse(detail, shouldRecordResponse);
        stateChannel.publish(() -> recordResponse);
    }

    private boolean shouldRecordResponse(NonBlockingOperationDetail detail, boolean shouldRecordResponse) {
        if (detail == null) {
            return false;
        } else if (!detail.shouldRecordResponse()) {
            return false;
        } else {
            return shouldRecordResponse;
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.NON_BLOCKING;
    }
}
