package io.github.dmitriyiliyov.idempify.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The module hands out a wrapper, not a store: what gets asserted is that the wrapper exists, that it nests
 * the redis decorator around whatever it is given, and under which conditions it is registered at all.
 */
class IdempifyRedisCacheAutoConfigurationIntegrationTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
            .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.type=DISTRIBUTED")
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
            .withBean(Clock.class, () -> Clock.fixed(NOW, java.time.ZoneOffset.UTC))
            .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"));

    @Test
    @DisplayName("IT context when all required beans exist should register template and cache wrapper")
    void context_whenAllRequiredBeansExist_shouldRegisterTemplateAndCacheWrapper() {
        // when / then
        runner.run(context -> {
            assertThat(context).hasSingleBean(ResponseRepositoryWrapper.class);
            assertThat(context).hasBean("idempifyRedisTemplate");
        });
    }

    @Test
    @DisplayName("IT context when the registered wrapper is asked to wrap should hand back the redis decorator")
    void context_whenRegisteredWrapperIsAskedToWrap_shouldHandBackRedisDecorator() {
        // when / then
        runner.run(context -> assertThat(
                context.getBean(ResponseRepositoryWrapper.class).wrap(mock(ResponseRepository.class)))
                .isInstanceOf(RedisCacheResponseRepositoryDecorator.class));
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
    @DisplayName("IT context when the template is registered should read back the record it writes")
    void context_whenTemplateIsRegistered_shouldReadBackRecordItWrites() {
        // when / then
        runner.run(context -> {
            RedisTemplate<?, ?> template = context.getBean("idempifyRedisTemplate", RedisTemplate.class);
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
            RawResponseContainer written = container();

            Object read = serializer.deserialize(serializer.serialize(written));

            assertThat(read).isInstanceOf(RawResponseContainer.class);
            RawResponseContainer readContainer = (RawResponseContainer) read;
            assertThat(readContainer.getResponse()).isEqualTo(written.getResponse());
            assertThat(readContainer.getFingerprint()).isEqualTo(written.getFingerprint());
            assertThat(readContainer.getExpiresAt()).isEqualTo(written.getExpiresAt());
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
        bareRunner()
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
                .withBean(Clock.class, () -> Clock.systemUTC())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when no RedisConnectionFactory exists should fail to start")
    void context_whenNoRedisConnectionFactoryExists_shouldFailToStart() {
        // when / then
        bareRunner()
                .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"))
                .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
                .withBean(Clock.class, () -> Clock.systemUTC())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("IT context when cacheName is null should fail to start")
    void context_whenCacheNameIsNull_shouldFailToStart() {
        // when / then
        bareRunner()
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
                .withBean(Clock.class, () -> Clock.systemUTC())
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
                .withBean(CacheEventListener.class, () -> CacheEventListener.NOOP)
                .withBean(Clock.class, () -> Clock.systemUTC())
                .withBean(CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("idempify"))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class));
    }

    @Test
    @DisplayName("IT context when the cache is switched off should register nothing")
    void context_whenCacheIsSwitchedOff_shouldRegisterNothing() {
        // when / then
        runner.withPropertyValues("idempify.cache.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class));
    }

    @Test
    @DisplayName("IT context when the cache type is in-memory should register no redis wrapper")
    void context_whenCacheTypeIsInMemory_shouldRegisterNoRedisWrapper() {
        // when / then
        runner.withPropertyValues("idempify.cache.type=IN_MEMORY")
                .run(context -> assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class));
    }

    @Test
    @DisplayName("IT context when RedisTemplate class is missing should register nothing")
    void context_whenRedisTemplateClassIsMissing_shouldRegisterNothing() {
        // when / then
        runner.withClassLoader(new FilteredClassLoader(RedisTemplate.class))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class));
    }

    @Test
    @DisplayName("IT context when ObjectMapper class is missing should register nothing")
    void context_whenObjectMapperClassIsMissing_shouldRegisterNothing() {
        // when / then
        runner.withClassLoader(new FilteredClassLoader(ObjectMapper.class))
                .run(context -> assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class));
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        // when / then
        runner.withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ResponseRepositoryWrapper.class);
                    assertThat(context).doesNotHaveBean("idempifyRedisTemplate");
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        // when / then
        runner.withPropertyValues("idempify.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseRepositoryWrapper.class);
                    assertThat(context).hasBean("idempifyRedisTemplate");
                });
    }

    private static ApplicationContextRunner bareRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyRedisCacheAutoConfiguration.class))
                .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.type=DISTRIBUTED");
    }

    private static RawResponseContainer container() {
        return new DefaultRawResponseContainer("raw-response", "fingerprint", NOW.plus(Duration.ofHours(1)));
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRedisTemplateConfiguration {

        @Bean
        RedisTemplate<String, RawResponseContainer> userRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
            RedisTemplate<String, RawResponseContainer> redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(redisConnectionFactory);
            return redisTemplate;
        }
    }
}
