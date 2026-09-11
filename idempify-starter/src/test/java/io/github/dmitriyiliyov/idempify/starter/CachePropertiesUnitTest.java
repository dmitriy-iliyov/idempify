package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.cache.CacheType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The properties are the outermost layer, so what is judged here is the refusal: a setting that cannot work
 * has to be named at startup, by the property a reader would go and edit.
 */
class CachePropertiesUnitTest {

    @Test
    @DisplayName("UT constructor() when enabled is null should throw NullPointerException")
    void constructor_whenEnabledIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(null, CacheType.IN_MEMORY, "orders", 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enabled cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when type is null should throw NullPointerException")
    void constructor_whenTypeIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, null, "orders", 100))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when capacity is null should throw NullPointerException")
    void constructor_whenCapacityIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, CacheType.IN_MEMORY, "orders", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("capacity cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when a distributed cache is on without a name should name the property at fault")
    void constructor_whenDistributedCacheIsOnWithoutName_shouldNamePropertyAtFault() {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, CacheType.DISTRIBUTED, "  ", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.cache.name' cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when a distributed cache is off should accept a missing name")
    void constructor_whenDistributedCacheIsOff_shouldAcceptMissingName() {
        // when
        CacheProperties result = new CacheProperties(false, CacheType.DISTRIBUTED, null, 100);

        // then
        assertThat(result.getName()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("UT constructor() when an in-memory cache holds nothing should name the property at fault")
    void constructor_whenInMemoryCacheHoldsNothing_shouldNamePropertyAtFault(int capacity) {
        // when / then
        assertThatThrownBy(() -> new CacheProperties(true, CacheType.IN_MEMORY, "orders", capacity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'idempify.cache.capacity' must be positive");
    }

    @Test
    @DisplayName("UT constructor() when the cache is distributed should not judge the capacity")
    void constructor_whenCacheIsDistributed_shouldNotJudgeCapacity() {
        // when
        CacheProperties result = new CacheProperties(true, CacheType.DISTRIBUTED, "orders", 0);

        // then
        assertThat(result.getCacheCapacity()).isZero();
    }

    @Test
    @DisplayName("UT toString() should name every setting it carries")
    void toString_shouldNameEverySettingItCarries() {
        // when
        String result = new CacheProperties(true, CacheType.DISTRIBUTED, "orders", 250).toString();

        // then
        assertThat(result).contains(
                "enabled=true",
                "type=DISTRIBUTED",
                "name='orders'",
                "capacity=250"
        );
    }

    @Test
    @DisplayName("UT getters should hand back everything the properties were built with")
    void getters_shouldHandBackEverythingPropertiesWereBuiltWith() {
        // when
        CacheProperties result = new CacheProperties(true, CacheType.IN_MEMORY, "orders", 250);

        // then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getType()).isEqualTo(CacheType.IN_MEMORY);
        assertThat(result.getName()).isEqualTo("orders");
        assertThat(result.getCacheCapacity()).isEqualTo(250);
    }
}
