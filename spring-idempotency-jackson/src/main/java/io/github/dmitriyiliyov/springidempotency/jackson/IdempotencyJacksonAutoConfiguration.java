package io.github.dmitriyiliyov.springidempotency.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.springidempotency.core.ResponseDeserializer;
import io.github.dmitriyiliyov.springidempotency.core.ResponseSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class IdempotencyJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ResponseSerializer.class)
    public ResponseSerializer jacksonResponseSerializer(ObjectMapper mapper) {
        return new JacksonResponseSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(ResponseDeserializer.class)
    public ResponseDeserializer jacksonResponseDeserializer(ObjectMapper mapper) {
        return new JacksonResponseDeserializer(mapper);
    }
}
