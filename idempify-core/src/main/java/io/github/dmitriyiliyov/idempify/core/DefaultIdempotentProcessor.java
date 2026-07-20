package io.github.dmitriyiliyov.idempify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

public class DefaultIdempotentProcessor implements IdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultIdempotentProcessor.class);
    private final TransactionTemplate transactionTemplate;
    private final OperationManager manager;
    private final IdempotencyEventListener eventListener;

    public DefaultIdempotentProcessor(TransactionTemplate transactionTemplate,
                                      OperationManager manager,
                                      IdempotencyEventListener eventListener) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate cannot be null");
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
    }

    @Override
    public <T> T process(OperationMetadata metadata, Class<T> resultType, IdempotentOperation<T> call) {
        return transactionTemplate.execute(status -> {
            try {
                Optional<T> existsResult = manager.startOrReply(metadata, resultType);

                if (existsResult.isPresent()) {
                    eventListener.onDuplicate();
                    return existsResult.get();
                }

                T result;
                try {
                    result = call.call();
                } catch (Throwable t) {
                    throw new RuntimeException("Error when processing surrounded method", t);
                }

                result = manager.complete(metadata, result);

                eventListener.onSuccess();

                return result;
            } catch (Exception e) {
                eventListener.onException();
                log.error("Error when idempotently processing operation (idempotencyKey={})", metadata.getIdempotencyKey());
                throw e;
            }
        });
    }
}
