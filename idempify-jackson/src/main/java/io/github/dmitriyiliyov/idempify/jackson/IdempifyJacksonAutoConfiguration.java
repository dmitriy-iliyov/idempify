package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
@ConditionalOnClass(ObjectMapper.class)
public class IdempifyJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResultSerializer idempifyJacksonResultSerializer(ObjectMapper mapper) {
        return new JacksonResultSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultDeserializer idempifyJacksonResultDeserializer(ObjectMapper mapper) {
        return new JacksonResultDeserializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(JsonBodyCanonicalizerCreator.class)
    public BodyCanonicalizerCreator idempifyJsonBodyCanonicalizerCreator(ObjectMapper mapper) {
        return new JsonBodyCanonicalizerCreator(mapper);
    }

}
