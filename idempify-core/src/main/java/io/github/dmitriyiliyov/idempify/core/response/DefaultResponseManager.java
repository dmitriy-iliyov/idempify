package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DefaultResponseManager implements ResponseManager {

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
            return Optional.empty();
        }
        RawResponseContainer container = nullableContainer.get();

        if (container.getResponse() == null) {
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
