package io.github.dmitriyiliyov.idempify.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.DefaultCachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyRedisCacheAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
            .withPropertyValues("idempify.cache.enabled=true")
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"));

    @Test
    @DisplayName("IT context when all required beans exist should register template and response cache")
    void context_whenAllRequiredBeansExist_shouldRegisterTemplateAndResponseCache() {
        // when / then
        runner.run(context -> {
            assertThat(context).hasSingleBean(ResponseCache.class);
            assertThat(context).hasSingleBean(RedisResponseCache.class);
            assertThat(context).hasBean("idempifyRedisTemplate");
        });
    }

    @Test
    @DisplayName("IT context when template is registered should carry a string key serializer")
    void context_whenTemplateIsRegistered_shouldCarryStringKeySerializer() {
        // when / then
        runner.run(context -> {
            RedisTemplate<?, ?> template = context.getBean("idempifyRedisTemplate", RedisTemplate.class);
            assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(template.getConnectionFactory()).isNotNull();
        });
    }

    @Test
    @DisplayName("IT context when template is registered should read back the response it writes")
    void context_whenTemplateIsRegistered_shouldReadBackTheResponseItWrites() {
        // when / then
        runner.run(context -> {
            RedisTemplate<?, ?> template = context.getBean("idempifyRedisTemplate", RedisTemplate.class);
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
            CachedResponse written = cachedResponse();

            Object read = serializer.deserialize(serializer.serialize(written));

            assertThat(read).isInstanceOf(CachedResponse.class);
            CachedResponse readResponse = (CachedResponse) read;
            assertThat(readResponse.getStatus()).isEqualTo(written.getStatus());
            assertThat(readResponse.getBody()).isEqualTo(written.getBody());
            assertThat(readResponse.getContentType()).isEqualTo(written.getContentType());
            assertThat(readResponse.getFingerprint()).isEqualTo(written.getFingerprint());
        });
    }

    @Test
    @DisplayName("IT context when existing ResponseCache bean should not register RedisResponseCache")
    void context_whenExistingResponseCacheBean_shouldNotRegisterRedisResponseCache() {
        // when / then
        runner.withUserConfiguration(UserResponseCacheConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseCache.class);
                    assertThat(context).doesNotHaveBean(RedisResponseCache.class);
                });
    }

    @Test
    @DisplayName("IT context when existing RedisTemplate bean should not register its own template")
    void context_whenExistingRedisTemplateBean_shouldNotRegisterItsOwnTemplate() {
        // when / then
        runner.withUserConfiguration(UserRedisTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasBean("userRedisTemplate");
                    assertThat(context).doesNotHaveBean("idempifyRedisTemplate");
                });
    }

    @Test
    @DisplayName("IT context when no CachePropertiesHolder exists should fail to start")
    void context_whenNoCachePropertiesHolderExists_shouldFailToStart() {
        // when / then
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
                .withPropertyValues("idempify.cache.enabled=true")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no RedisConnectionFactory exists should fail to start")
    void context_whenNoRedisConnectionFactoryExists_shouldFailToStart() {
        // when / then
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
                .withPropertyValues("idempify.cache.enabled=true")
                .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when cacheName is null should fail to start")
    void context_whenCacheNameIsNull_shouldFailToStart() {
        // when / then
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
                .withPropertyValues("idempify.cache.enabled=true")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder(null))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when the cache switch is missing should register nothing")
    void context_whenCacheSwitchIsMissing_shouldRegisterNothing() {
        // when / then
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseCache.class));
    }

    @Test
    @DisplayName("IT context when the cache is switched off should register nothing")
    void context_whenCacheIsSwitchedOff_shouldRegisterNothing() {
        // when / then
        runner.withPropertyValues("idempify.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ResponseCache.class));
    }

    @Test
    @DisplayName("IT context when RedisTemplate class is missing should register nothing")
    void context_whenRedisTemplateClassIsMissing_shouldRegisterNothing() {
        // when / then
        runner.withClassLoader(new FilteredClassLoader(RedisTemplate.class))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseCache.class));
    }

    @Test
    @DisplayName("IT context when ObjectMapper class is missing should register nothing")
    void context_whenObjectMapperClassIsMissing_shouldRegisterNothing() {
        // when / then
        runner.withClassLoader(new FilteredClassLoader(ObjectMapper.class))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseCache.class));
    }

    private CachedResponse cachedResponse() {
        return new DefaultCachedResponse(
                201,
                "{\"id\":1}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                "fingerprint"
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class UserResponseCacheConfiguration {

        @Bean
        ResponseCache userResponseCache() {
            return new ResponseCache() {

                @Override
                public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
                    return null;
                }

                @Override
                public void save(UUID idempotencyKey, CachedResponse response, Duration ttl) { }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRedisTemplateConfiguration {

        @Bean
        RedisTemplate<String, CachedResponse> userRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
            RedisTemplate<String, CachedResponse> redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(redisConnectionFactory);
            return redisTemplate;
        }
    }
}
