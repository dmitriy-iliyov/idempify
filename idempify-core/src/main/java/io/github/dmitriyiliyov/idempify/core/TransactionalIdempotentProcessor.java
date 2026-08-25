package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

public class TransactionalIdempotentProcessor implements TypeAwareIdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionalIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;
    private final TransactionalOperationManager operationManager;
    private final IdempotencyEventListener eventListener;

    public TransactionalIdempotentProcessor(TransactionTemplate transactionTemplate,
                                            TransactionalOperationManager operationManager,
                                            IdempotencyEventListener eventListener) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        this.operationManager = Objects.requireNonNull(operationManager, "operationManager cannot be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
    }

    @Override
    public <T> T process(OperationContext<T> context, OperationMetadata metadata) {
        return transactionTemplate.execute(
                status -> {
                    try {
                        Optional<T> nullableResult = operationManager.startOrReply(context, metadata);
                        if (nullableResult.isPresent()) {
                            return nullableResult.get();
                        }

                        T result = getResult(context.getOperationCallback());
                        result = operationManager.complete(context.getIdempotencyKey(), result);

                        eventListener.onSuccess();

                        return result;
                    } catch (Throwable t) {
                        log.error("Error when idempotently processing operation (idempotencyKey={})", context.getIdempotencyKey(), t);
                        eventListener.onException();

                        if (t instanceof RuntimeException re) {
                            throw re;
                        }
                        throw new IdempotentProcessingException("Error when processing surrounded method", t);
                    }
                }
        );
    }

    private <T> T getResult(ExternalOperationCallback<T> operationCallback) {
        try {
            return operationCallback.call();
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new IdempotentProcessingException("Surrounded method throws", t);
        }
    }

    @Override
    public ProcessorType getType() {
        return ProcessorType.TRANSACTIONAL;
    }
}
