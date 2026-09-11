package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.SerializationException;

import java.util.Objects;

final class GenericJacksonSerializer {

    private final ObjectMapper mapper;

    GenericJacksonSerializer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    String serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException jpe) {
            throw new SerializationException("Error when serializing %s".formatted(obj.getClass().getName()), jpe);
        }
    }
}
