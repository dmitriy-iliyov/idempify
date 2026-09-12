package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public class TransactionalIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionalIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;
    private final TransactionalOperationManager operationManager;
    private final OperationStateChannel channel;
    private final IdempotencyEventListener eventListener;

    public TransactionalIdempotentProcessor(TransactionTemplate transactionTemplate,
                                            TransactionalOperationManager operationManager,
                                            OperationStateChannel channel,
                                            IdempotencyEventListener eventListener) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        this.operationManager = Objects.requireNonNull(operationManager, "operationManager cannot be null");
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
    }

    /**
     * Publishing the state and reporting the events happen after {@code execute} returns, and have to stay
     * there: until the transaction commits the operation does not exist, so a state published earlier would
     * announce a row that may never be, and a success would be counted for one that never happened.
     */
    @Override
    public Object process(OperationContext context, OperationMetadata metadata) {
        try {
            OperationDetail operationDetail = transactionTemplate.execute(status -> {
                transactionStatusCheck(status);

                OperationDetail operation = operationManager.startOrReply(context, metadata);

                if (OperationStatus.PROCESSED.equals(operation.getStatus())) {
                    return operation;
                }

                Object result = IdempotentProcessorUtils.getResult(context.getCallback());
                operation = operationManager.complete(
                        operation.getIdempotencyKey(),
                        result,
                        metadata.getTtl()
                );
                return operation;
            });

            channel.publish(operationDetail);
            if (operationDetail.replayed()) {
                eventListener.onDuplicate();
            } else {
                eventListener.onSuccess();
            }

            return operationDetail.getResult();
        } catch (Throwable t) {
            log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), t);
            eventListener.onException();

            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new IdempotentProcessingException("Error when processing surrounded method", t);
        }
    }

    protected void transactionStatusCheck(TransactionStatus status) {
        if (!status.isNewTransaction()) {
            throw new IllegalStateException("""
                        @Idempotent must own its transaction: it is meant for an entry point, 
                        not for a method already running inside a transaction
                    """);
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.TRANSACTIONAL;
    }
}
