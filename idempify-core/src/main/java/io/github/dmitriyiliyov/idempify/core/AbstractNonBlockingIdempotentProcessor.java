package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The common skeleton of the non-blocking processors: the key is claimed under a lease outside any transaction,
 * a call that met another call's claim is settled by the call site's {@code ConflictHandler}, and a call that
 * took the claim gives it back when it is done - unless a subclass keeps it to hold off a repeat, as
 * {@link ExternalNonBlockingIdempotentProcessor} does when an outcome could not be written.
 * <p>
 * What a failed business method leaves behind is decided here for both subclasses, by the call site's
 * {@code recordFor}: a failure it names is kept on the record and replayed to every repeat until the record
 * expires, any other releases the key for the next attempt.
 *
 * @see NonBlockingOperationManager
 */
public abstract class AbstractNonBlockingIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(AbstractNonBlockingIdempotentProcessor.class);
    protected final NonBlockingOperationManager manager;
    protected final OperationStateChannel stateChannel;
    protected final IdempotencyEventListener eventListener;

    /**
     * Wraps {@code eventListener} so that a failing listener is logged and never reaches the caller: by the time an
     * event is sent, the outcome of the operation is decided, and a metric has no right to change it.
     */
    public AbstractNonBlockingIdempotentProcessor(NonBlockingOperationManager manager,
                                                  OperationStateChannel stateChannel,
                                                  IdempotencyEventListener eventListener) {
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
        this.stateChannel = Objects.requireNonNull(stateChannel, "stateChannel cannot be null");
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
    }

    /**
     * Refuses a call made inside a transaction already open on this thread: the claim has to be visible to a
     * duplicate before the business method starts, which a claim written in somebody else's transaction is not.
     *
     * @throws ForeignTransactionException if a transaction is active.
     */
    protected void isTransactionExists() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ForeignTransactionException("""
                    @Idempotent with %s processor must not run inside a transaction: its record lives apart from the \
                    business effect, so it is meant for an entry point, not for a method already running inside one"""
                    .formatted(getType()));
        }
    }

    /**
     * Claims the key. A failure to do so is logged and rethrown as {@link OperationLockFailedException}; a
     * {@link FingerprintException} - the call carries no usable fingerprint, so nothing was claimed - and an
     * {@link Error} pass through untouched.
     */
    protected LockDetail tryLock(OperationContext context, OperationMetadata metadata) {
        try {
            return manager.tryLock(context, metadata);
        } catch (FingerprintException fe) {
            throw fe;
        } catch (Exception e) {
            log.error("Error when claiming operation (idempotencyKey={})", context.getIdempotencyKey(), e);
            throw new OperationLockFailedException(context.getIdempotencyKey(), e);
        }
    }

    /**
     * Reads the operation behind the key and checks the call against it. A refusal - a {@link ConflictException}
     * or a {@link FingerprintException} - and an {@link IdempotentProcessingException} are rethrown as they are;
     * any other failure is rethrown as {@link EarlyFailureException}, since the business method has not run. An
     * {@link Error} passes through untouched.
     */
    protected NonBlockingOperationDetail fetchAndValidate(OperationContext context,
                                                          OperationMetadata metadata,
                                                          LockDetail lockDetail) {
        try {
            return manager.fetchAndValidate(context, metadata, lockDetail);
        } catch (IdempotentProcessingException | ConflictException | FingerprintException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error when fetching and validating operation (idempotencyKey={})", context.getIdempotencyKey(), e);
            throw new EarlyFailureException(context.getIdempotencyKey(), e);
        }
    }

    /**
     * Decides the fate of the key after the business method threw. A failure that
     * {@link IdempotentProcessorUtils#libraryFailureTypeOf} names - a misplaced call such as a refused nested one,
     * an interruption, a failure of the library before or after an operation - releases the key whatever
     * {@code recordFor} says: none is an outcome of the operation. Otherwise a type named in the call site's
     * {@code recordFor}, matched with {@link Class#isInstance}, is kept on the record for the call site's TTL,
     * and any other failure releases the key.
     * <p>
     * The interrupt flag is read as it stands when the failure is handled, so an interruption that arrives after a
     * failure the call site would keep releases that failure too, while one wrapped in another type with the flag
     * cleared is not recognised. The flag itself is left as it is: the failure goes back to the caller unchanged.
     * <p>
     * Runs outside the transaction the business method ran in, if there was one: that transaction rolls back
     * with the failure, and what is written here has to survive it.
     *
     * @param t the failure of the business method.
     * @param context the call that failed.
     * @param metadata the resolved settings of its call site.
     * @param lockToken the token this call holds the claim under.
     * @param progress the progress of this call, marked once the key is released.
     * @return whether the transport should record a response - {@code true} exactly when the failure was kept.
     * @throws FailureNotRecordedException if the key could neither be released nor the failure recorded,
     *         carrying {@code t} as suppressed; an {@link Error} passes through untouched.
     */
    protected boolean guardBusinessFailure(Throwable t,
                                           OperationContext context,
                                           OperationMetadata metadata,
                                           UUID lockToken,
                                           IdempotentProcessorUtils.CallProgress progress) {
        List<Class<? extends Throwable>> recordForList = metadata.getResponseConfig().getRecordFor();
        try {
            boolean isFailureRecorded;
            FailureType libraryFailureType = IdempotentProcessorUtils.libraryFailureTypeOf(t);
            if (libraryFailureType != null) {
                log.debug("Operation (idempotencyKey={}) failed for a reason of its call: {}", context.getIdempotencyKey(), libraryFailureType);
                isFailureRecorded = false;
                releaseKey(context, lockToken, progress);
            } else {
                isFailureRecorded = isRecordable(t, recordForList);

                if (isFailureRecorded) {
                    manager.completeExceptionally(context, t, metadata.getTtl(), lockToken);
                    eventListener.onExceptionallySuccess();
                } else {
                    releaseKey(context, lockToken, progress);
                    eventListener.onBusinessFailure();
                }
            }
            return isFailureRecorded;
        } catch (Exception e) {
            log.error("Error when releasing or recording a failed operation (idempotencyKey={})", context.getIdempotencyKey(), e);
            throw new FailureNotRecordedException(context.getIdempotencyKey(), e, t);
        }
    }

    /**
     * Marks the key released only once the record is gone: a removal that failed leaves the claim in place, and
     * it still has to be given back.
     */
    private void releaseKey(OperationContext context, UUID lockToken, IdempotentProcessorUtils.CallProgress progress) {
        manager.rollback(context.getIdempotencyKey(), lockToken);
        progress.markKeyReleased();
    }

    /**
     * Whether {@code t} is an instance of any type in {@code recordForList}; an empty list records nothing.
     */
    protected boolean isRecordable(Throwable t, List<Class<? extends Throwable>> recordForList) {
        for (Class<? extends Throwable> c : recordForList) {
            if (c.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code t} is the kept failure this call replays rather than one it ran into: the first is the
     * operation's answer, not an error of this call.
     */
    protected boolean isReplayedFailure(NonBlockingOperationDetail detail, Throwable t) {
        return detail != null
                && OperationStatus.EXCEPTIONALLY_PROCESSED.equals(detail.getStatus())
                && detail.getResult().equals(t);
    }

    /**
     * Tells the transport whether to record a response. A failure of the channel is logged and swallowed: the
     * outcome is decided by then, and the claim still has to be given back.
     */
    protected void publishState(NonBlockingOperationDetail detail, boolean shouldRecordResponse) {
        try {
            final boolean recordResponse = shouldRecordResponse(detail, shouldRecordResponse);
            stateChannel.publish(() -> recordResponse);
        } catch (Exception e) {
            log.error("Error when publishing operation state", e);
        }
    }

    /**
     * Never {@code true} without a detail or for one that has nothing new to record, such as a replay;
     * otherwise what the processor decided.
     */
    protected boolean shouldRecordResponse(NonBlockingOperationDetail detail, boolean shouldRecordResponse) {
        if (detail == null) {
            return false;
        } else if (!detail.shouldRecordResponse()) {
            return false;
        } else {
            return shouldRecordResponse;
        }
    }

    /**
     * Gives the claim back, unless the key was released by removing the record - there is no claim left then. A
     * failure to do so is logged and swallowed: the outcome of the call is decided by then, and a claim that was
     * not given back runs out with its lease.
     */
    protected void unlock(OperationContext context,
                          LockDetail lockDetail,
                          IdempotentProcessorUtils.CallProgress progress) {
        try {
            if (!progress.isKeyReleased()) {
                manager.unlock(context.getIdempotencyKey(), lockDetail.getLockToken());
            }
        } catch (Exception e) {
            log.error("Error when unlocking operation (idempotencyKey={})", context.getIdempotencyKey(), e);
        }
    }
}
