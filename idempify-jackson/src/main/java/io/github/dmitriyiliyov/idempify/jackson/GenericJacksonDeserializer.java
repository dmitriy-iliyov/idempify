package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.DeserializationException;

import java.util.Objects;

final class GenericJacksonDeserializer {

    private final ObjectMapper mapper;

    GenericJacksonDeserializer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    Object deserialize(String rawResult, Class<?> c) {
        if (rawResult == null) {
            return null;
        }
        try {
            return mapper.readValue(rawResult, c);
        } catch (JsonProcessingException jpe) {
            throw new DeserializationException("Error when deserializing %s".formatted(c.getName()), jpe);
        }
    }
}
