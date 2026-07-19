package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.ResultSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class IdempifyJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResultSerializer jacksonResponseSerializer(ObjectMapper mapper) {
        return new JacksonResultSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultDeserializer jacksonResponseDeserializer(ObjectMapper mapper) {
        return new JacksonResultDeserializer(mapper);
    }
}
