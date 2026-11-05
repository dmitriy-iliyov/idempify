package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantee the non-blocking claim stands on and a single thread cannot break: of many calls racing for one
 * key, exactly one takes it - whether the key is free or its last lease has run out. Every party claims on a
 * connection of its own, and every wait on a row lock is bounded by the database ({@code lock_timeout}), so a
 * broken claim fails the run instead of hanging it.
 */
@Testcontainers
class PostgreSqlNonBlockingOperationRepositoryMultiThreadTest {

    private static final long FIVE_MINUTES = Duration.ofMinutes(5).toMillis();
    private static final int PARTIES = 8;
    private static final long WAIT_SECONDS = 30;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlNonBlockingOperationRepository tested;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("options", "-c lock_timeout=20000");
        dataSource.setConnectionProperties(connectionProperties);
        jdbcClient = JdbcClient.create(dataSource);
        try {
            jdbcClient.sql(new ClassPathResource("idempotent_operations_table.sql")
                    .getContentAsString(StandardCharsets.UTF_8)).update();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        tested = new PostgreSqlNonBlockingOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @RepeatedTest(20)
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT lockOrSaveAndLock() when callers claim a free key at once should let exactly one take it")
    void lockOrSaveAndLock_whenCallersClaimFreeKeyAtOnce_shouldLetExactlyOneTakeIt() throws Exception {
        // given
        UUID key = UUID.randomUUID();

        // when
        List<Claim> claims = claimAtOnce(key);

        // then
        assertOneHolder(key, claims);
    }

    @RepeatedTest(20)
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT lockOrSaveAndLock() when callers take over a key whose lease ran out at once should let exactly one take it")
    void lockOrSaveAndLock_whenCallersTakeOverKeyWhoseLeaseRanOutAtOnce_shouldLetExactlyOneTakeIt() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        assertThat(tested.lockOrSaveAndLock(claim(key, UUID.randomUUID()), FIVE_MINUTES))
                .as("the first holder took the free key")
                .isTrue();
        jdbcClient.sql("UPDATE idempotent_operations SET lock_until = clock_timestamp() - INTERVAL '1 second'")
                .update();

        // when
        List<Claim> claims = claimAtOnce(key);

        // then
        assertOneHolder(key, claims);
    }

    @RepeatedTest(20)
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT lockOrSaveAndLock() when callers claim a key that was let go at once should let exactly one take it")
    void lockOrSaveAndLock_whenCallersClaimKeyThatWasLetGoAtOnce_shouldLetExactlyOneTakeIt() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        UUID firstHolder = UUID.randomUUID();
        assertThat(tested.lockOrSaveAndLock(claim(key, firstHolder), FIVE_MINUTES))
                .as("the first holder took the free key")
                .isTrue();
        tested.unlock(key, firstHolder);

        // when
        List<Claim> claims = claimAtOnce(key);

        // then
        assertOneHolder(key, claims);
    }

    private List<Claim> claimAtOnce(UUID key) throws Exception {
        CountDownLatch ready = new CountDownLatch(PARTIES);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Claim>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(PARTIES, named("claimer"))) {
            for (int i = 0; i < PARTIES; i++) {
                UUID lockToken = UUID.randomUUID();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    return new Claim(lockToken, tested.lockOrSaveAndLock(claim(key, lockToken), FIVE_MINUTES));
                }));
            }
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("every claimer reached the gate").isTrue();

            start.countDown();
            List<Claim> claims = new ArrayList<>();
            for (Future<Claim> future : futures) {
                claims.add(future.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
            return claims;
        }
    }

    private void assertOneHolder(UUID key, List<Claim> claims) {
        List<Claim> winners = claims.stream().filter(Claim::isAcquired).toList();
        assertThat(winners).as("claimers told they took the key").hasSize(1);
        assertThat(tested.find(key)).as("the row after the race").hasValueSatisfying(row ->
                assertThat(row.lockToken()).as("token the key is held under").isEqualTo(winners.getFirst().lockToken())
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch was never released");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }

    private static ThreadFactory named(String name) {
        AtomicInteger created = new AtomicInteger();
        return runnable -> new Thread(runnable, name + "-" + created.getAndIncrement());
    }

    private static RawNonBlockingOperation claim(UUID idempotencyKey, UUID lockToken) {
        return new RawNonBlockingOperation(
                idempotencyKey,
                OperationStatus.IN_PROCESS,
                false,
                null,
                null,
                null,
                "fingerprint",
                null,
                null,
                lockToken,
                null
        );
    }

    private record Claim(UUID lockToken, boolean isAcquired) { }
}
