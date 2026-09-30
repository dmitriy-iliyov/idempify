package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public class BlockingIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(BlockingIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;
    private final BlockingOperationManager operationManager;
    private final OperationStateChannel stateChannel;
    private final IdempotencyEventListener eventListener;

    public BlockingIdempotentProcessor(TransactionTemplate transactionTemplate,
                                       BlockingOperationManager operationManager,
                                       OperationStateChannel stateChannel,
                                       IdempotencyEventListener eventListener) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        this.operationManager = Objects.requireNonNull(operationManager, "operationManager cannot be null");
        this.stateChannel = Objects.requireNonNull(stateChannel, "stateChannel cannot be null");
        this.eventListener = new SwallowIdempotencyEventListenerDecorator(
                Objects.requireNonNull(eventListener, "eventListener cannot be null")
        );
    }

    /**
     * Publishing the state and reporting the events happen after {@code execute} returns, and have to stay
     * there: until the transaction commits the operation does not exist, so a state published earlier would
     * announce a row that may never be, and a success would be counted for one that never happened.
     * <p>
     * What the business method throws leaves the transaction wrapped, because a transaction callback cannot throw
     * a checked exception, and is unwrapped once the transaction has rolled back: the caller receives it as it
     * was thrown.
     */
    @Override
    public Object process(OperationContext context, OperationMetadata metadata) throws Throwable {
        try {
            OperationDetail operationDetail = transactionTemplate.execute(status -> {
                IdempotentProcessorUtils.transactionStatusCheck(status);

                OperationDetail detail = operationManager.startOrReply(context, metadata);

                if (OperationStatus.PROCESSED.equals(detail.getStatus())) {
                    return detail;
                }

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
        } catch (MethodFailureHolder mfh) {
            log.debug("Operation (idempotencyKey={}) ended exceptionally", context.getIdempotencyKey(), mfh.getCause());
            eventListener.onException();
            throw mfh.getCause();
        } catch (Throwable t) {
            log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), t);
            eventListener.onException();
            throw t;
        }
    }

    private Object call(OperationContext context) {
        try {
            return context.getCallback().call();
        } catch (Throwable t) {
            throw new MethodFailureHolder(t);
        }
    }

    private void publishState(OperationState state) {
        stateChannel.publish(state);
        if (state.shouldRecordResponse()) {
            eventListener.onSuccess();
        } else {
            eventListener.onDuplicate();
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.BLOCKING;
    }

    private static final class MethodFailureHolder extends RuntimeException {
        private MethodFailureHolder(Throwable cause) {
            super(null, cause, false, false);
        }
    }
}
