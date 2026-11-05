package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static io.github.dmitriyiliyov.idempify.core.IdempotentProcessorUtils.*;

/**
 * Runs {@link ProcessorType#TRANSACTIONAL_BLOCKING}: the claim, the business method and the record of its
 * result share one transaction the processor owns, so a duplicate blocks on the claim until the first call
 * commits, and a failure rolls the record back together with the effect, leaving the key free.
 * <p>
 * The template has to be able to open a transaction of its own - {@code REQUIRED}, {@code REQUIRES_NEW} or
 * {@code NESTED}, which the constructor checks - and a transaction already open around the call is refused
 * inside {@code execute}, before the store is touched.
 * <p>
 * The guarantee holds at {@code READ COMMITTED} only: under {@code REPEATABLE READ} or {@code SERIALIZABLE} the
 * duplicate that waited on the claim does not see the committed row and fails with a serialization error
 * instead of being answered with the result. The level of the transaction is not checked.
 */
public class TransactionalBlockingIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionalBlockingIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;
    private final BlockingOperationManager operationManager;
    private final OperationStateChannel stateChannel;
    private final IdempotencyEventListener eventListener;

    public TransactionalBlockingIdempotentProcessor(TransactionTemplate transactionTemplate,
                                                    BlockingOperationManager operationManager,
                                                    OperationStateChannel stateChannel,
                                                    IdempotencyEventListener eventListener) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        validateTransactionPropagation(transactionTemplate);
        this.operationManager = Objects.requireNonNull(operationManager, "operationManager cannot be null");
        this.stateChannel = Objects.requireNonNull(stateChannel, "stateChannel cannot be null");
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
     }

    /**
     * Publishing the state and reporting the events happen after {@code execute} returns, and have to stay
     * there: until the transaction commits the operation does not exist, so a state published earlier would
     * announce a row that may never be, and a success would be counted for one that never happened. A failure of
     * the channel is logged and swallowed: by then the operation is committed, and its outcome stands.
     * <p>
     * What the business method throws leaves the transaction wrapped, because a transaction callback cannot throw
     * a checked exception, and is unwrapped once the transaction has rolled back: the caller receives it as it
     * was thrown.
     * <p>
     * Any other failure is named by whether the business method had started: one before it - opening the
     * transaction, claiming the key, committing a replay - reaches the caller as {@link EarlyFailureException},
     * one after it - completing the operation, committing or rolling it back - as
     * {@link OutcomeNotRecordedException}, either carrying it as the cause. A failure that already says what it is
     * - an {@link IdempotentProcessingException}, a {@link ConflictException} or a {@link FingerprintException} -
     * and an {@link Error} are thrown as they are. The failure is counted above the processor, by its type, so
     * nothing here reports it.
     */
    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        CallProgress progress = new CallProgress();
        try {
            OperationDetail operationDetail = transactionTemplate.execute(status -> {
                validateTransactionStatus(status);

                OperationDetail detail = operationManager.startOrReply(context, metadata);

                if (OperationStatus.PROCESSED.equals(detail.getStatus())) {
                    return detail;
                }

                progress.markOperationStarted();

                Object result = call(context);
                detail = operationManager.complete(
                        detail.getIdempotencyKey(),
                        result,
                        metadata.getTtl()
                );
                return detail;
            });

            publishState(operationDetail);
            return operationDetail.getResult();
        } catch (BusinessOperationFailureHolder bofh) {
            Throwable t = bofh.getCause();
            log.debug("Operation (idempotencyKey={}) ended exceptionally", context.getIdempotencyKey(), t);
            if (libraryFailureTypeOf(t) == null) {
                eventListener.onBusinessFailure();
            }
            throw t;
        } catch (IdempotentProcessingException | ConflictException | FingerprintException e) {
            log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), e);
            throw progress.isOperationStarted()
                    ? new ResultNotRecordedException(context.getIdempotencyKey(), e)
                    : new EarlyFailureException(context.getIdempotencyKey(), e);
        }
    }

    private Object call(OperationContext context) {
        try {
            return context.getCallback().call();
        } catch (Throwable t) {
            throw new BusinessOperationFailureHolder(t);
        }
    }

    private void publishState(OperationState state) {
        try {
            stateChannel.publish(state);
        } catch (Exception e) {
            log.error("Error when publishing operation state", e);
        }
        if (state.shouldRecordResponse()) {
            eventListener.onSuccess();
        } else {
            eventListener.onDuplicate();
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.TRANSACTIONAL_BLOCKING;
    }

    private static final class BusinessOperationFailureHolder extends RuntimeException {
        private BusinessOperationFailureHolder(Throwable cause) {
            super(null, cause, false, false);
        }
    }
}
