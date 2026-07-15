package io.github.dmitriyiliyov.springidempotency.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
    public <T> T process(OperationMetadata metadata, Supplier<T> supplier) {
        return transactionTemplate.execute(status -> {
            try {
                Optional<T> existsResponse = manager.startOrReply(metadata, (Class<T>) Object.class);

                if (existsResponse.isPresent()) {
                    eventListener.onDuplicate();
                    return existsResponse.get();
                }

                T response = supplier.get();
                response = manager.complete(metadata, response);

                eventListener.onSuccess();

                return response;
            } catch (Exception e) {
                eventListener.onException();
                log.error("Error when idempotently processing operation (idempotencyKey={})", metadata.getIdempotencyKey());
                throw e;
            }
        });
    }
}
