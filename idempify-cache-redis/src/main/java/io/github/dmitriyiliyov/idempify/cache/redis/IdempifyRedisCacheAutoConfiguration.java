package io.github.dmitriyiliyov.idempify.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.github.dmitriyiliyov.idempify.core.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.IdempifyCoreAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.StringUtils;
import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.cache.CacheType;
import io.github.dmitriyiliyov.idempify.core.cache.ConditionalOnCacheType;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
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

import java.time.Clock;

@AutoConfiguration(after = RedisAutoConfiguration.class, before = IdempifyCoreAutoConfiguration.class)
@ConditionalOnIdempifyEnabled
@ConditionalOnClass({RedisTemplate.class, ObjectMapper.class})
@ConditionalOnProperty(
        prefix = "idempify.cache",
        name = "enabled",
        havingValue = "true"
)
public class IdempifyRedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, RawResponseContainer> idempifyRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, RawResponseContainer> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer()
                .configure(objectMapper -> {
                    objectMapper.registerModule(new ParameterNamesModule());
                    objectMapper.registerModule(new JavaTimeModule());
                });
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setDefaultSerializer(valueSerializer);
        return redisTemplate;
    }

    @Bean
    @ConditionalOnCacheType(type = CacheType.DISTRIBUTED)
    public ResponseRepositoryWrapper idempifyRedisCacheResponseRepositoryWrapper(
            RedisTemplate<String, RawResponseContainer> redisTemplate,
            CachePropertiesHolder holder,
            CacheEventListener listener,
            Clock clock
    ) {
        String cacheName = holder.getName();
        if (StringUtils.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName cannot be null, blank or empty");
        }
        return new ResponseRepositoryWrapper() {
            @Override
            public ResponseRepository wrap(ResponseRepository repository) {
                return new RedisCacheResponseRepositoryDecorator(
                        repository,
                        redisTemplate,
                        cacheName,
                        listener,
                        clock
                );
            }

            @Override
            public int getPriority() {
                return Integer.MIN_VALUE;
            }
        };
    }
}
