package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializationException;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.ResultType;

public class JacksonResultDeserializer implements ResultDeserializer {

    private final ObjectMapper mapper;

    public JacksonResultDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Object deserialize(String rawResult, ResultType type) {
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(type.getType());
            return mapper.readValue(rawResult, javaType);
        } catch (JsonProcessingException jpe) {
            throw new ResultDeserializationException("Error when deserializing operation result", jpe);
        }
    }
}
