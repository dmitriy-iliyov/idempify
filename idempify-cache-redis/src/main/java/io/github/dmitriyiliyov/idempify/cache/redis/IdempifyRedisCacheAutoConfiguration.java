package io.github.dmitriyiliyov.idempify.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.github.dmitriyiliyov.idempify.core.IdempifyCoreAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Objects;

@AutoConfiguration(after = RedisAutoConfiguration.class, before = IdempifyCoreAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnClass({RedisTemplate.class, ObjectMapper.class})
@ConditionalOnProperty(
        prefix = "idempify.cache",
        name = "enabled",
        havingValue = "true"
)
public class IdempifyRedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, CachedResponse> idempifyRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, CachedResponse> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer()
                .configure(objectMapper -> objectMapper.registerModule(new ParameterNamesModule()));
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setDefaultSerializer(valueSerializer);
        return redisTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseCache idempifyRedisResponseCache(RedisTemplate<String, CachedResponse> redisTemplate,
                                                    CachePropertiesHolder holder) {
        return new RedisResponseCache(
                redisTemplate,
                Objects.requireNonNull(holder.getCacheName(), "cacheName cannot be null")
        );
    }
}
