package io.github.dmitriyiliyov.idempify.core.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DefaultResponseManager implements ResponseManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultResponseManager.class);
    private final ResponseRepository repository;
    private final ResponseSerializer serializer;
    private final ResponseDeserializer deserializer;

    public DefaultResponseManager(ResponseRepository repository,
                                  ResponseSerializer serializer,
                                  ResponseDeserializer deserializer) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer cannot be null");
    }

    @Override
    public void save(UUID idempotencyKey, Response response) {
        if (idempotencyKey == null || response == null) {
            return;
        }
        repository.save(idempotencyKey, serializer.serialize(response));
    }

    @Override
    public Optional<ResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
        Optional<RawResponseContainer> nullableContainer = repository.findByIdempotencyKey(idempotencyKey);
        if (nullableContainer.isEmpty()) {
            log.debug("Operation (idempotencyKey={}) has no record, so there is no response to replay", idempotencyKey);
            return Optional.empty();
        }
        RawResponseContainer container = nullableContainer.get();

        if (container.getResponse() == null) {
            log.debug("Operation (idempotencyKey={}) has not answered yet, so there is no response to replay", idempotencyKey);
            return Optional.empty();
        }

        Long remainingMillis = container.getRemainingMillis();
        if (remainingMillis == null) {
            log.warn("Operation (idempotencyKey={}) carries a response but no expiry, so it is not replayed", idempotencyKey);
            return Optional.empty();
        }

        if (remainingMillis <= 0) {
            log.debug(
                    "Operation (idempotencyKey={}) response expired {} ms ago, so it is not replayed",
                    idempotencyKey,
                    -remainingMillis
            );
            return Optional.empty();
        }

        return Optional.of(
                new DefaultResponseContainer(
                        deserializer.deserialize(container.getResponse()),
                        container.getFingerprint()
                )
        );
    }
}
