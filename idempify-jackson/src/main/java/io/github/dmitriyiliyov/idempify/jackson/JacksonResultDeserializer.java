package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;

public class JacksonResultDeserializer implements ResultDeserializer {

    private final ObjectMapper mapper;

    public JacksonResultDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> T deserialize(String rawResult, Class<T> c) {
        try {
            return mapper.readValue(rawResult, c);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error when deserializing operation result", jpe);
        }
    }
}
