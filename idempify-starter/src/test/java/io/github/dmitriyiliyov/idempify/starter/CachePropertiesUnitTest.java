package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachePropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(null, "orders", false, false, inMemory()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when shouldCache4xx is null should throw NullPointerException")
    void constructor_whenShouldCache4xxIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, "orders", null, false, inMemory()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("shouldCache4xx cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when shouldCache5xx is null should throw NullPointerException")
    void constructor_whenShouldCache5xxIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, "orders", false, null, inMemory()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("shouldCache5xx cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when caching is enabled without a cache name should name both ways out")
    void constructor_whenCachingIsEnabledWithoutCacheName_shouldNameBothWaysOut() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, null, false, false, inMemory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempify.cache.cache-name")
                .hasMessageContaining("idempify.cache.enabled");
    }

    @Test
    @DisplayName("UT constructor() when caching is enabled with a blank cache name should throw IllegalArgumentException")
    void constructor_whenCachingIsEnabledWithBlankCacheName_shouldThrowIllegalArgumentException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, "   ", false, false, inMemory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheName cannot be null, empty or blank");
    }

    @Test
    @DisplayName("UT constructor() when caching is disabled should not ask for a cache name")
    void constructor_whenCachingIsDisabled_shouldNotAskForCacheName() {
        // when
        CacheProperties tested = new CacheProperties(false, null, false, false, inMemory());

        // then
        assertThat(tested.isEnabled()).isFalse();
        assertThat(tested.getCacheName()).isNull();
    }

    @Test
    @DisplayName("UT getCacheName() when a name is set should hand the backend that very name")
    void getCacheName_whenNameIsSet_shouldHandBackendThatVeryName() {
        // when
        CacheProperties tested = new CacheProperties(true, "orders", false, false, inMemory());

        // then
        assertThat(tested.getCacheName()).isEqualTo("orders");
    }

    @Test
    @DisplayName("UT toResponseCacheConfig() when caching is enabled should decide every flag")
    void toResponseCacheConfig_whenCachingIsEnabled_shouldDecideEveryFlag() {
        // given
        CacheProperties tested = new CacheProperties(true, "orders", true, true, inMemory());

        // when
        ResponseCacheConfig result = tested.toResponseCacheConfig();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.shouldCache4xx()).isTrue();
        assertThat(result.shouldCache5xx()).isTrue();
    }

    @Test
    @DisplayName("UT toResponseCacheConfig() when nothing is asked for should leave every status out of the cache")
    void toResponseCacheConfig_whenNothingIsAskedFor_shouldLeaveEveryStatusOutOfCache() {
        // given
        CacheProperties tested = new CacheProperties(true, "orders", false, false, inMemory());

        // when
        ResponseCacheConfig result = tested.toResponseCacheConfig();

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.shouldCache4xx()).isFalse();
        assertThat(result.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT toResponseCacheConfig() when caching is disabled should say so instead of leaving it open")
    void toResponseCacheConfig_whenCachingIsDisabled_shouldSaySoInsteadOfLeavingItOpen() {
        // given
        CacheProperties tested = new CacheProperties(false, null, false, false, inMemory());

        // when
        ResponseCacheConfig result = tested.toResponseCacheConfig();

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(ResponseCacheConfig.merge(result, ResponseCacheConfig.all()).isEnabled())
                .describedAs("a call site cannot turn back on what the properties switched off")
                .isFalse();
    }

    @Test
    @DisplayName("UT shouldCache4xx() and shouldCache5xx() should answer what was configured")
    void shouldCache4xxAndShouldCache5xx_shouldAnswerWhatWasConfigured() {
        // given
        CacheProperties tested = new CacheProperties(true, "orders", true, false, inMemory());

        // when / then
        assertThat(tested.shouldCache4xx()).isTrue();
        assertThat(tested.shouldCache5xx()).isFalse();
    }

    @Test
    @DisplayName("UT toString() should name every property it carries")
    void toString_shouldNameEveryPropertyItCarries() {
        // given
        CacheProperties tested = new CacheProperties(true, "orders", true, false, inMemory());

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains(
                "enabled=true",
                "cacheName='orders'",
                "shouldCache4xx=true",
                "shouldCache5xx=false",
                "capacity=100"
        );
    }

    @Test
    @DisplayName("UT constructor() when the in-memory block is null should throw NullPointerException")
    void constructor_whenInMemoryBlockIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, "orders", false, false, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("inMemory cannot be null");
    }

    @Test
    @DisplayName("UT getInMemoryCacheCapacity() should hand the fallback store the capacity the block carries")
    void getInMemoryCacheCapacity_shouldHandFallbackStoreCapacityBlockCarries() {
        // given
        CacheProperties tested = new CacheProperties(true, "orders", false, false, inMemory(25));

        // when / then
        assertThat(tested.getInMemoryCacheCapacity()).isEqualTo(25);
    }

    @Test
    @DisplayName("UT getInMemoryCacheCapacity() when caching is disabled should still answer a capacity")
    void getInMemoryCacheCapacity_whenCachingIsDisabled_shouldStillAnswerCapacity() {
        // given
        CacheProperties tested = new CacheProperties(false, null, false, false, inMemory());

        // when / then
        assertThat(tested.getInMemoryCacheCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("UT InMemoryCacheProperties constructor when capacity is null should throw NullPointerException")
    void inMemoryConstructor_whenCapacityIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties.InMemoryCacheProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("capacity cannot be null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("UT InMemoryCacheProperties constructor when the capacity holds nothing should name the property at fault")
    void inMemoryConstructor_whenCapacityHoldsNothing_shouldNamePropertyAtFault(int capacity) {
        // when / then
        assertThatThrownBy(() -> new CacheProperties.InMemoryCacheProperties(capacity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempify.cache.in-memory.capacity must be positive");
    }

    private static CacheProperties.InMemoryCacheProperties inMemory() {
        return inMemory(100);
    }

    private static CacheProperties.InMemoryCacheProperties inMemory(int capacity) {
        return new CacheProperties.InMemoryCacheProperties(capacity);
    }
}
