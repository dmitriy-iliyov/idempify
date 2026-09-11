package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.ProcessorType;
import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfig;
import io.github.dmitriyiliyov.idempify.core.config.IdempotencyConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IdempifyAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyAutoConfiguration.class));

    @Test
    @DisplayName("IT autoConfiguration should be registered so that an application only adds the dependency")
    void autoConfiguration_shouldBeRegisteredSoThatApplicationOnlyAddsDependency() {
        // when
        Iterable<String> candidates = ImportCandidates.load(
                org.springframework.boot.autoconfigure.AutoConfiguration.class, getClass().getClassLoader()
        );

        // then
        assertThat(candidates).contains(IdempifyAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("IT context when nothing is configured should register the global config and the cache name holder")
    void context_whenNothingIsConfigured_shouldRegisterGlobalConfigAndCacheNameHolder() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IdempotencyConfig.class);
            assertThat(context).hasBean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME);
            assertThat(context).hasSingleBean(CachePropertiesHolder.class);
            assertThat(context).hasSingleBean(IdempifyProperties.class);
        });
    }

    @Test
    @DisplayName("IT context when the library is switched off should leave nothing of it in the context")
    void context_whenLibraryIsSwitchedOff_shouldLeaveNothingOfItInContext() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyConfig.class);
                    assertThat(context).doesNotHaveBean(CachePropertiesHolder.class);
                    assertThat(context).doesNotHaveBean(IdempifyProperties.class);
                });
    }

    @Test
    @DisplayName("IT context when the library is switched on by name should configure idempotency as usual")
    void context_whenLibraryIsSwitchedOnByName_shouldConfigureIdempotencyAsUsual() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyConfig.class);
                    assertThat(context).hasSingleBean(CachePropertiesHolder.class);
                });
    }

    @Test
    @DisplayName("IT context should hand the core a config the properties describe")
    void context_shouldHandCoreConfigPropertiesDescribe() {
        contextRunner
                .withPropertyValues("idempify.header-name=X-Request-Id")
                .run(context -> {
                    IdempotencyConfig config = context.getBean(IdempotencyConfig.class);
                    assertThat(config.getHeaderName()).isEqualTo("X-Request-Id");
                    assertThat(config.notEmpty()).isTrue();
                });
    }

    @Test
    @DisplayName("IT context should bind the properties as the provider the core asks for")
    void context_shouldBindPropertiesAsProviderCoreAsksFor() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyConfigProvider.class);
            assertThat(context.getBean(IdempotencyConfigProvider.class))
                    .isSameAs(context.getBean(IdempifyProperties.class));
        });
    }

    @Test
    @DisplayName("IT context when the cache block names a cache should tell the backend that name")
    void context_whenCacheBlockNamesCache_shouldTellBackendThatName() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.name=orders")
                .run(context -> assertThat(context.getBean(CachePropertiesHolder.class).getName())
                        .isEqualTo("orders"));
    }

    @Test
    @DisplayName("IT context when no cache is asked for should still answer the backend with no name")
    void context_whenNoCacheIsAskedFor_shouldStillAnswerBackendWithNoName() {
        contextRunner.run(context -> assertThat(context.getBean(CachePropertiesHolder.class).getName()).isNull());
    }

    @Test
    @DisplayName("IT context when an existing defaultIdempotencyConfig bean should not build another one")
    void context_whenExistingDefaultIdempotencyConfigBean_shouldNotBuildAnotherOne() {
        contextRunner
                .withUserConfiguration(ExistingConfigConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyConfig.class);
                    assertThat(context.getBean(IdempotencyConfig.class).getHeaderName()).isEqualTo("X-Hand-Written");
                });
    }

    @Test
    @DisplayName("IT context when another IdempotencyConfigProvider bean exists should keep building from the properties")
    void context_whenAnotherIdempotencyConfigProviderBeanExists_shouldKeepBuildingFromProperties() {
        contextRunner
                .withPropertyValues("idempify.header-name=X-From-Properties")
                .withBean("userConfigProvider", IdempotencyConfigProvider.class,
                        () -> IdempifyAutoConfigurationIntegrationTest::handWrittenConfig)
                .run(context -> {
                    assertThat(context)
                            .describedAs("the provider is the seam over the properties, not a bean the starter looks up")
                            .hasNotFailed();
                    assertThat(context.getBean(IdempotencyConfig.class).getHeaderName())
                            .isEqualTo("X-From-Properties");
                });
    }

    @Test
    @DisplayName("IT context when a named config is declared as a bean should still build the global one")
    void context_whenNamedConfigIsDeclaredAsBean_shouldStillBuildGlobalOne() {
        contextRunner
                .withPropertyValues("idempify.header-name=X-From-Properties")
                .withBean("paymentsConfig", IdempotencyConfig.class,
                        IdempifyAutoConfigurationIntegrationTest::handWrittenConfig)
                .run(context -> {
                    assertThat(context)
                            .describedAs("an application may declare as many named configs as it has call sites")
                            .hasNotFailed();
                    assertThat(context).hasBean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME);
                    assertThat(context.getBean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME, IdempotencyConfig.class)
                            .getHeaderName()).isEqualTo("X-From-Properties");
                });
    }

    @Test
    @DisplayName("IT context when caching is asked for without a capacity should hand the fallback store the built-in one")
    void context_whenCachingIsAskedForWithoutCapacity_shouldHandFallbackStoreBuiltInOne() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true")
                .run(context ->
                        assertThat(context.getBean(CachePropertiesHolder.class).getCacheCapacity()).isEqualTo(100));
    }

    @Test
    @DisplayName("IT context when the cache block names a capacity should hand the fallback store that number")
    void context_whenCacheBlockNamesCapacity_shouldHandFallbackStoreThatNumber() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.capacity=25")
                .run(context ->
                        assertThat(context.getBean(CachePropertiesHolder.class).getCacheCapacity()).isEqualTo(25));
    }

    @Test
    @DisplayName("IT context when the capacity of an asked-for cache holds nothing should fail naming the property at fault")
    void context_whenCapacityOfAskedForCacheHoldsNothing_shouldFailNamingPropertyAtFault() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.capacity=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("'idempify.cache.capacity' must be positive");
                });
    }

    @Test
    @DisplayName("IT context when an existing CachePropertiesHolder bean should not register the properties one")
    void context_whenExistingCachePropertiesHolderBean_shouldNotRegisterPropertiesOne() {
        contextRunner
                .withBean("userCachePropertiesHolder", CachePropertiesHolder.class, () -> new TestCachePropertiesHolder("user-cache"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CachePropertiesHolder.class);
                    assertThat(context).doesNotHaveBean("idempifyCachePropertiesHolder");
                    assertThat(context.getBean(CachePropertiesHolder.class).getName()).isEqualTo("user-cache");
                });
    }

    @Test
    @DisplayName("IT context when the properties are rejected should fail naming the property at fault")
    void context_whenPropertiesAreRejected_shouldFailNamingPropertyAtFault() {
        contextRunner
                .withPropertyValues("idempify.cache.enabled=true", "idempify.cache.type=DISTRIBUTED")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("'idempify.cache.name'");
                });
    }

    @Test
    @DisplayName("IT context when the application is not a web one should configure idempotency anyway")
    void context_whenApplicationIsNotWebOne_shouldConfigureIdempotencyAnyway() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IdempotencyConfig.class);
            assertThat(context).hasSingleBean(CachePropertiesHolder.class);
        });
    }

    private static IdempotencyConfig handWrittenConfig() {
        return IdempotencyConfig.builder()
                .headerName("X-Hand-Written")
                .ttl(Duration.ofHours(1))
                .processorType(ProcessorType.LOCK_BASED)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class ExistingConfigConfiguration {

        @Bean(IdempifyDefaults.DEFAULT_CONFIG_BEAN_NAME)
        IdempotencyConfig globalConfig() {
            return handWrittenConfig();
        }
    }

    private static final class TestCachePropertiesHolder implements CachePropertiesHolder {

        private final String cacheName;

        private TestCachePropertiesHolder(String cacheName) {
            this.cacheName = cacheName;
        }

        @Override
        public String getName() {
            return cacheName;
        }

        @Override
        public int getCacheCapacity() {
            return 100;
        }
    }
}
