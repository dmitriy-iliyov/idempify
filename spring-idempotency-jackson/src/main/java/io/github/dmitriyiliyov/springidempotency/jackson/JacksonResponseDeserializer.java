package io.github.dmitriyiliyov.springidempotency.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.springidempotency.core.ResponseDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JacksonResponseDeserializer implements ResponseDeserializer {

    private final ObjectMapper mapper;

    public JacksonResponseDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> T deserialize(String rawResponse, Class<T> c) {
        try {
            return mapper.readValue(rawResponse, c);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error when deserializing operation response", jpe);
        }
    }
}
