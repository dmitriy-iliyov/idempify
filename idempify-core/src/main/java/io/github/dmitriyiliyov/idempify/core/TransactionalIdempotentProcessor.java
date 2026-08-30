package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Override
    public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
        try {
            OperationDetail<T> operationDetail = transactionTemplate.execute(status -> {
                OperationDetail<T> operation = operationManager.startOrReply(context, metadata);

                if (OperationStatus.PROCESSED.equals(operation.getStatus())) {
                    return operation;
                }

                T result = IdempotentProcessorUtils.getResult(context.getOperationCallback());
                operation = operationManager.complete(context.getIdempotencyKey(), result);
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

    @Override
    public ProcessorType getType() {
        return ProcessorType.TRANSACTIONAL;
    }
}
