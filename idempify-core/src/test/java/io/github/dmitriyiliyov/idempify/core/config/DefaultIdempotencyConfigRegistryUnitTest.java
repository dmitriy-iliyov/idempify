package io.github.dmitriyiliyov.idempify.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultIdempotencyConfigRegistryUnitTest {

    @Test
    @DisplayName("UT get() when the name was never registered should return null")
    void get_whenNameWasNeverRegistered_shouldReturnNull() {
        assertThat(new DefaultIdempotencyConfigRegistry().get("payments")).isNull();
    }

    @Test
    @DisplayName("UT get() when the name is null should return null")
    void get_whenNameIsNull_shouldReturnNull() {
        assertThat(new DefaultIdempotencyConfigRegistry().get(null)).isNull();
    }

    @Test
    @DisplayName("UT register() should hand the config back and make it findable by name")
    void register_shouldHandConfigBackAndMakeItFindableByName() {
        // given
        DefaultIdempotencyConfigRegistry tested = new DefaultIdempotencyConfigRegistry();
        IdempotencyConfig config = config(Duration.ofHours(1));

        // when
        IdempotencyConfig result = tested.register("payments", config);

        // then
        assertThat(result).isSameAs(config);
        assertThat(tested.get("payments")).isSameAs(config);
    }

    @Test
    @DisplayName("UT register() when the name is already taken should throw IllegalStateException")
    void register_whenNameIsAlreadyTaken_shouldThrowIllegalStateException() {
        // given
        DefaultIdempotencyConfigRegistry tested = new DefaultIdempotencyConfigRegistry();
        tested.register("payments", config(Duration.ofHours(1)));

        // when / then
        assertThatThrownBy(() -> tested.register("payments", config(Duration.ofHours(2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payments")
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("UT register() when the name is null should throw NullPointerException")
    void register_whenNameIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotencyConfigRegistry().register(null, config(Duration.ofHours(1))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name cannot be null");
    }

    @Test
    @DisplayName("UT register() when the config is null should throw NullPointerException")
    void register_whenConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotencyConfigRegistry().register("payments", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("UT register() when the name is blank should throw IllegalArgumentException")
    void register_whenNameIsBlank_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new DefaultIdempotencyConfigRegistry().register("   ", config(Duration.ofHours(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be blank");
    }

    @Test
    @DisplayName("UT constructor when seeded with configs should make every one of them findable")
    void constructor_whenSeededWithConfigs_shouldMakeEveryOneOfThemFindable() {
        // given
        IdempotencyConfig payments = config(Duration.ofHours(1));
        IdempotencyConfig orders = config(Duration.ofHours(2));

        // when
        DefaultIdempotencyConfigRegistry tested =
                new DefaultIdempotencyConfigRegistry(Map.of("payments", payments, "orders", orders));

        // then
        assertThat(tested.get("payments")).isSameAs(payments);
        assertThat(tested.get("orders")).isSameAs(orders);
    }

    @Test
    @DisplayName("UT constructor when the seed map is null should throw NullPointerException")
    void constructor_whenSeedMapIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new DefaultIdempotencyConfigRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configs cannot be null");
    }

    @Test
    @DisplayName("UT register() when several threads race for the same name should let exactly one of them win")
    void register_whenSeveralThreadsRaceForSameName_shouldLetExactlyOneOfThemWin() throws Exception {
        // given
        int threads = 16;
        DefaultIdempotencyConfigRegistry tested = new DefaultIdempotencyConfigRegistry();
        List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        tested.register("payments", config(Duration.ofHours(1)));
                        return true;
                    } catch (IllegalStateException e) {
                        return false;
                    }
                })
                .toList();

        // when
        List<Future<Boolean>> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            results = executor.invokeAll(tasks);
        }

        // then
        long winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        assertThat(winners).isEqualTo(1);
    }

    @Test
    @DisplayName("UT toString() should name the configs it holds")
    void toString_shouldNameConfigsItHolds() {
        // given
        DefaultIdempotencyConfigRegistry tested = new DefaultIdempotencyConfigRegistry();
        tested.register("payments", config(Duration.ofHours(1)));

        // then
        assertThat(tested.toString()).contains("payments");
    }

    @Test
    @DisplayName("UT constructor when a seeded name is blank should throw IllegalArgumentException")
    void constructor_whenSeededNameIsBlank_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new DefaultIdempotencyConfigRegistry(Map.of("   ", config(Duration.ofHours(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be blank");
    }

    @Test
    @DisplayName("UT constructor when seeded with nothing should hold no config")
    void constructor_whenSeededWithNothing_shouldHoldNoConfig() {
        // given
        DefaultIdempotencyConfigRegistry tested = new DefaultIdempotencyConfigRegistry(Map.of());

        // when / then
        assertThat(tested.get("payments")).isNull();
    }

    private IdempotencyConfig config(Duration ttl) {
        return IdempotencyConfig.builder().ttl(ttl).build();
    }
}
