package io.github.dmitriyiliyov.springidempotency.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.springidempotency.core.ResponseSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JacksonResponseSerializer implements ResponseSerializer {

    private final ObjectMapper mapper;

    public JacksonResponseSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> String serialize(T response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error when serializing operation response", jpe);
        }
    }
}
