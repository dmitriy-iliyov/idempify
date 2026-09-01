package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ResultSerializationException;
import io.github.dmitriyiliyov.idempify.core.ResultSerializer;

public class JacksonResultSerializer implements ResultSerializer {

    private final ObjectMapper mapper;

    public JacksonResultSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String serialize(Object result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException jpe) {
            throw new ResultSerializationException("Error when serializing operation result", jpe);
        }
    }
}
