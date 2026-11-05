package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantee the blocking branch stands on and a single thread cannot break: the claim taken by
 * {@code saveIfAbsent} is held until the caller's transaction ends, not until its statement does. Every party
 * runs in a transaction of its own on a connection of its own, and every wait on a row lock is bounded by the
 * database ({@code lock_timeout}), so a broken claim fails the run instead of hanging it.
 */
@Testcontainers
class PostgreSqlBlockingOperationRepositoryMultiThreadTest {

    private static final long AN_HOUR = Duration.ofHours(1).toMillis();
    private static final int PARTIES = 8;
    private static final long WAIT_SECONDS = 30;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private static TransactionTemplate transactions;
    private PostgreSqlBlockingOperationRepository tested;

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
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        try {
            jdbcClient.sql(new ClassPathResource("idempotent_operations_table.sql")
                    .getContentAsString(StandardCharsets.UTF_8)).update();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        tested = new PostgreSqlBlockingOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT saveIfAbsent() when a second caller claims a held key should wait for the commit and see the first result")
    void saveIfAbsent_whenSecondCallerClaimsHeldKey_shouldWaitForCommitAndSeeFirstResult() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2, named("claim-holder", "duplicate"))) {
            Future<?> first = pool.submit(() -> transactions.executeWithoutResult(status -> {
                tested.saveIfAbsent(claim(key));
                claimed.countDown();
                await(release);
                tested.saveResultAndUpdateStatus(key, "first-result", OperationStatus.PROCESSED, AN_HOUR,
                        OperationStatus.IN_PROCESS);
            }));
            await(claimed);

            // when
            Future<RawOperation> second = pool.submit(() -> transactions.execute(status -> tested.saveIfAbsent(claim(key))));
            awaitBlockedOnLock();
            boolean doneWhileFirstHeldClaim = second.isDone();
            release.countDown();
            first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            RawOperation result = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(doneWhileFirstHeldClaim).as("duplicate answered while the claim was held").isFalse();
            assertThat(result.isFirstAttempt()).as("duplicate took the key as its own").isFalse();
            assertThat(result.status()).as("status the duplicate saw").isEqualTo(OperationStatus.PROCESSED);
            assertThat(result.result()).as("result the duplicate saw").isEqualTo("first-result");
        }
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT saveIfAbsent() when the holder of a key rolls back should hand the key to the caller waiting on it")
    void saveIfAbsent_whenHolderOfKeyRollsBack_shouldHandKeyToCallerWaitingOnIt() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2, named("claim-holder", "duplicate"))) {
            Future<?> first = pool.submit(() -> transactions.executeWithoutResult(status -> {
                tested.saveIfAbsent(claim(key));
                claimed.countDown();
                await(release);
                status.setRollbackOnly();
            }));
            await(claimed);

            // when
            Future<RawOperation> second = pool.submit(() -> transactions.execute(status -> tested.saveIfAbsent(claim(key))));
            awaitBlockedOnLock();
            release.countDown();
            first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            RawOperation result = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(result.isFirstAttempt()).as("waiting caller took the released key").isTrue();
            assertThat(result.status()).as("status the waiting caller claimed").isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(result.result()).as("result left by the rolled-back holder").isNull();
        }
    }

    @RepeatedTest(20)
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("MT saveIfAbsent() when callers claim one key at once should let exactly one run and answer the rest with its result")
    void saveIfAbsent_whenCallersClaimOneKeyAtOnce_shouldLetExactlyOneRunAndAnswerRestWithItsResult() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(PARTIES);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger mostHoldingAtOnce = new AtomicInteger();
        List<Future<Claim>> claims = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(PARTIES, named("caller"))) {
            for (int i = 0; i < PARTIES; i++) {
                String party = "result-of-" + i;
                claims.add(pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    return transactions.execute(status -> {
                        RawOperation claimedRow = tested.saveIfAbsent(claim(key));
                        if (claimedRow.isFirstAttempt()) {
                            mostHoldingAtOnce.accumulateAndGet(holding.incrementAndGet(), Math::max);
                            tested.saveResultAndUpdateStatus(key, party, OperationStatus.PROCESSED, AN_HOUR,
                                    OperationStatus.IN_PROCESS);
                            holding.decrementAndGet();
                        }
                        return new Claim(party, claimedRow);
                    });
                }));
            }
            assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("every caller reached the gate").isTrue();

            // when
            start.countDown();
            List<Claim> results = new ArrayList<>();
            for (Future<Claim> claim : claims) {
                results.add(claim.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }

            // then
            List<Claim> winners = results.stream().filter(claim -> claim.row().isFirstAttempt()).toList();
            assertThat(winners).as("callers that took the key").hasSize(1);
            assertThat(mostHoldingAtOnce.get()).as("callers holding the key at once").isEqualTo(1);
            String winnersResult = winners.getFirst().party();
            assertThat(results).filteredOn(claim -> !claim.row().isFirstAttempt())
                    .as("what every duplicate was answered with")
                    .allSatisfy(claim -> {
                        assertThat(claim.row().status()).isEqualTo(OperationStatus.PROCESSED);
                        assertThat(claim.row().result()).isEqualTo(winnersResult);
                    });
        }
    }

    /**
     * Waits for a connection to be parked on a row lock, which is what "the claim is held" looks like from inside
     * the database - rather than for a guessed amount of time.
     */
    private static void awaitBlockedOnLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcClient.sql("""
                    SELECT count(*)::int
                    FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND datname = current_database()
                    """)
                    .query(Integer.class)
                    .single();
            if (waiting > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("no connection ended up waiting on the claim");
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

    private static ThreadFactory named(String... names) {
        AtomicInteger created = new AtomicInteger();
        return runnable -> {
            int index = created.getAndIncrement();
            String name = index < names.length ? names[index] : names[names.length - 1] + "-" + index;
            return new Thread(runnable, name);
        };
    }

    private static RawOperation claim(UUID idempotencyKey) {
        return new RawOperation(
                idempotencyKey,
                OperationStatus.IN_PROCESS,
                true,
                null,
                null,
                "fingerprint",
                null,
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private record Claim(String party, RawOperation row) { }
}
