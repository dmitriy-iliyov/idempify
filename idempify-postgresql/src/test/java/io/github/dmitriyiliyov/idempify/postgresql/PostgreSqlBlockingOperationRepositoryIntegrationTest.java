package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guarantees this store owes the core are the ones a mock cannot have: the claim is atomic,
 * first-writer-wins and held until the caller's transaction ends, both updates are conditional on the row's
 * current status, and the expiry exists on a completed operation and nowhere else - counted by the database's
 * own clock and read back as the time a row has left. All of it is judged against a real Postgres.
 * <p>
 * The repository deals in rows only - no serializer takes part here, so what is written is what comes back.
 * Where a statement is meant to carry the whole record, that is asserted on the record and not field by
 * field: a column added to the table and to {@link RawOperation} but left out of the statement passes every
 * hand-picked assertion there is. The two columns the database fills itself - the creation time and the
 * remaining time - are the exception, and are compared as the database answered them.
 */
@Testcontainers
class PostgreSqlBlockingOperationRepositoryIntegrationTest {

    private static final long AN_HOUR = Duration.ofHours(1).toMillis();
    private static final long TOLERANCE = Duration.ofMinutes(1).toMillis();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbcClient;
    private PostgreSqlBlockingOperationRepository tested;
    private PostgreSqlNonBlockingOperationRepository reader;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
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
        tested = new PostgreSqlBlockingOperationRepository(jdbcClient);
        reader = new PostgreSqlNonBlockingOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @Test
    @DisplayName("IT constructor() when jdbcClient is null should throw NullPointerException")
    void constructor_whenJdbcClientIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new PostgreSqlBlockingOperationRepository(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("jdbcClient cannot be null");
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is free should insert the row and call it a first attempt")
    void saveIfAbsent_whenKeyIsFree_shouldInsertRowAndCallItFirstAttempt() {
        // given
        UUID key = UUID.randomUUID();

        // when
        RawOperation result = tested.saveIfAbsent(claim(key));

        // then
        assertThat(result.idempotencyKey()).isEqualTo(key);
        assertThat(result.status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.result()).isNull();
        assertThat(result.fingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is free should write every column its statement names")
    void saveIfAbsent_whenKeyIsFree_shouldWriteEveryColumnItsStatementNames() {
        // given
        RawOperation toClaim = fullClaim(UUID.randomUUID());

        // when
        RawOperation result = tested.saveIfAbsent(toClaim);

        // then
        assertThat(result).isEqualTo(databaseFilled(toClaim, result));
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is free should date the row by the database clock")
    void saveIfAbsent_whenKeyIsFree_shouldDateRowByDatabaseClock() {
        // given
        Instant before = databaseNow();

        // when
        RawOperation result = tested.saveIfAbsent(claim(UUID.randomUUID()));

        // then
        assertThat(result.createdAt()).isBetween(before, databaseNow());
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is taken should hand back the stored row and call it a repeat")
    void saveIfAbsent_whenKeyIsTaken_shouldHandBackStoredRowAndCallItRepeat() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));

        // when
        RawOperation result = tested.saveIfAbsent(claim(key));

        // then
        assertThat(result.idempotencyKey()).isEqualTo(key);
        assertThat(result.isFirstAttempt()).isFalse();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is taken should not overwrite the status or the result it found")
    void saveIfAbsent_whenKeyIsTaken_shouldNotOverwriteStatusOrResultItFound() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        tested.saveResultAndUpdateStatus(key, "original-result", OperationStatus.PROCESSED, AN_HOUR,
                OperationStatus.IN_PROCESS);

        // when
        RawOperation result = tested.saveIfAbsent(claim(key));

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.result()).isEqualTo("original-result");
        assertThat(result.remainingMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is claimed should leave the expiry and the response empty")
    void saveIfAbsent_whenKeyIsClaimed_shouldLeaveExpiryAndResponseEmpty() {
        // when
        RawOperation result = tested.saveIfAbsent(claim(UUID.randomUUID()));

        // then
        assertThat(result.remainingMillis()).isNull();
        assertThat(result.response()).isNull();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when a second caller claims the same key should wait until the first transaction commits")
    void saveIfAbsent_whenSecondCallerClaimsSameKey_shouldWaitUntilFirstTransactionCommits() throws Exception {
        // given
        UUID key = UUID.randomUUID();
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            tested.saveIfAbsent(claim(key));
            claimed.countDown();
            await(release);
            tested.saveResultAndUpdateStatus(key, "first-result", OperationStatus.PROCESSED, AN_HOUR,
                    OperationStatus.IN_PROCESS);
        }));
        await(claimed);

        // when
        CompletableFuture<RawOperation> second = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> tested.saveIfAbsent(claim(key)))
        );
        awaitBlockedOnLock();
        boolean doneWhileFirstHeldClaim = second.isDone();
        release.countDown();
        first.get(30, TimeUnit.SECONDS);
        RawOperation result = second.get(30, TimeUnit.SECONDS);

        // then
        assertThat(doneWhileFirstHeldClaim).isFalse();
        assertThat(result.isFirstAttempt()).isFalse();
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.result()).isEqualTo("first-result");
    }

    @Test
    @DisplayName("IT update() when the status matches should rewrite every column and hand the row back")
    void update_whenStatusMatches_shouldRewriteEveryColumnAndHandRowBack() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        RawOperation rewritten = fullRewrite(key);

        // when
        RawOperation result = tested.update(rewritten, OperationStatus.IN_PROCESS);

        // then
        assertThat(result).isEqualTo(databaseFilled(rewritten, result));
    }

    @Test
    @DisplayName("IT update() should ignore the remaining time it is handed and leave the row without an expiry")
    void update_shouldIgnoreRemainingTimeItIsHandedAndLeaveRowWithoutExpiry() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));

        // when
        RawOperation result = tested.update(fullRewrite(key), OperationStatus.IN_PROCESS);

        // then
        assertThat(result.remainingMillis()).isNull();
    }

    @Test
    @DisplayName("IT update() when the status does not match should throw OperationStatusMismatchException")
    void update_whenStatusDoesNotMatch_shouldThrowOperationStatusMismatchException() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        RawOperation rewritten = completed(key);

        // when / then
        assertThatThrownBy(() -> tested.update(rewritten, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());
    }

    @Test
    @DisplayName("IT update() when the status does not match should leave the stored row exactly as it was")
    void update_whenStatusDoesNotMatch_shouldLeaveStoredRowExactlyAsItWas() {
        // given
        UUID key = UUID.randomUUID();
        RawOperation claimed = tested.saveIfAbsent(claim(key));

        // when
        assertThatThrownBy(() -> tested.update(completed(key), OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class);

        // then
        assertThat(stored(key)).isEqualTo(claimed);
    }

    @Test
    @DisplayName("IT update() when no row is stored under the key should throw OperationStatusMismatchException")
    void update_whenNoRowIsStoredUnderKey_shouldThrowOperationStatusMismatchException() {
        // given
        UUID key = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> tested.update(completed(key), OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationStatusMismatchException.class);
    }

    @Test
    @DisplayName("IT update() when an expired row is reclaimed should clear the expiry and the response it inherited")
    void update_whenExpiredRowIsReclaimed_shouldClearExpiryAndResponseItInherited() {
        // given - a completed row that has outlived its expiry and still carries the answer it replayed
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        tested.saveResultAndUpdateStatus(key, "stale-result", OperationStatus.PROCESSED, -AN_HOUR,
                OperationStatus.IN_PROCESS);
        givenStoredResponse(key);

        // when
        RawOperation result = tested.update(claim(key), OperationStatus.PROCESSED);

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.remainingMillis()).isNull();
        assertThat(result.response()).isNull();
        assertThat(result.result()).isNull();
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the status matches should store the result and the new status")
    void saveResultAndUpdateStatus_whenStatusMatches_shouldStoreResultAndNewStatus() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));

        // when
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, AN_HOUR,
                OperationStatus.IN_PROCESS);

        // then
        RawOperation stored = stored(key);
        assertThat(stored.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(stored.result()).isEqualTo("result");
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() should be what puts an expiry on a row that had none")
    void saveResultAndUpdateStatus_shouldBeWhatPutsExpiryOnRowThatHadNone() {
        // given
        UUID key = UUID.randomUUID();
        assertThat(tested.saveIfAbsent(claim(key)).remainingMillis()).isNull();

        // when
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, AN_HOUR,
                OperationStatus.IN_PROCESS);

        // then
        assertThat(stored(key).remainingMillis()).isBetween(AN_HOUR - TOLERANCE, AN_HOUR);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the ttl is already spent should read back as expired")
    void saveResultAndUpdateStatus_whenTtlIsAlreadySpent_shouldReadBackAsExpired() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));

        // when
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, -AN_HOUR,
                OperationStatus.IN_PROCESS);

        // then
        assertThat(stored(key).remainingMillis()).isNegative();
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() should not touch the response the row carries")
    void saveResultAndUpdateStatus_shouldNotTouchResponseRowCarries() {
        // given - the answer is written by its own update, after the operation committed
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        givenStoredResponse(key);

        // when
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, AN_HOUR,
                OperationStatus.IN_PROCESS);

        // then
        assertThat(stored(key).response()).isEqualTo("raw-response");
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the status does not match should throw and leave the row untouched")
    void saveResultAndUpdateStatus_whenStatusDoesNotMatch_shouldThrowAndLeaveRowUntouched() {
        // given
        UUID key = UUID.randomUUID();
        RawOperation claimed = tested.saveIfAbsent(claim(key));

        // when
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED,
                AN_HOUR, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());

        // then
        assertThat(stored(key)).isEqualTo(claimed);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when no row is stored under the key should throw")
    void saveResultAndUpdateStatus_whenNoRowIsStoredUnderKey_shouldThrow() {
        // given
        UUID key = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED,
                AN_HOUR, OperationStatus.IN_PROCESS))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the same key completes again should replace the previous expiry")
    void saveResultAndUpdateStatus_whenSameKeyCompletesAgain_shouldReplacePreviousExpiry() {
        // given
        UUID key = UUID.randomUUID();
        long fiveHours = Duration.ofHours(5).toMillis();
        tested.saveIfAbsent(claim(key));
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, AN_HOUR,
                OperationStatus.IN_PROCESS);
        tested.update(claim(key), OperationStatus.PROCESSED);

        // when
        tested.saveResultAndUpdateStatus(key, "result", OperationStatus.PROCESSED, fiveHours,
                OperationStatus.IN_PROCESS);

        // then
        assertThat(stored(key).remainingMillis()).isBetween(fiveHours - TOLERANCE, fiveHours);
    }

    @Test
    @DisplayName("IT find() when the row exists should return every column it holds")
    void find_whenRowExists_shouldReturnEveryColumnItHolds() {
        // given
        UUID key = UUID.randomUUID();
        RawOperation claimed = tested.saveIfAbsent(claim(key));

        // when
        RawOperation result = stored(key);

        // then
        assertThat(result).isEqualTo(claimed);
    }

    @Test
    @DisplayName("IT find() when no row is stored under the key should return an empty optional")
    void find_whenNoRowIsStoredUnderKey_shouldReturnEmptyOptional() {
        // when / then
        assertThat(reader.find(UUID.randomUUID())).isEmpty();
    }

    /**
     * The row as the read side hands it back, narrowed to the columns a blocking row has.
     */
    private RawOperation stored(UUID idempotencyKey) {
        RawNonBlockingOperation row = reader.find(idempotencyKey).orElseThrow();
        return new RawOperation(
                row.idempotencyKey(),
                row.status(),
                row.isFirstAttempt(),
                row.result(),
                row.response(),
                row.fingerprint(),
                row.remainingMillis(),
                row.createdAt()
        );
    }

    /**
     * The row that was handed over, with the two columns the database fills itself taken from what it answered.
     */
    private static RawOperation databaseFilled(RawOperation written, RawOperation answered) {
        return new RawOperation(
                written.idempotencyKey(),
                written.status(),
                written.isFirstAttempt(),
                written.result(),
                written.response(),
                written.fingerprint(),
                answered.remainingMillis(),
                answered.createdAt()
        );
    }

    private void givenStoredResponse(UUID idempotencyKey) {
        jdbcClient.sql("UPDATE idempotent_operations SET response = ? WHERE idempotency_key = ?")
                .params("raw-response", idempotencyKey)
                .update();
    }

    private static Instant databaseNow() {
        return jdbcClient.sql("SELECT clock_timestamp()").query(Timestamp.class).single().toInstant();
    }

    /**
     * Waits for the second connection to be parked on a row lock, which is what "the claim is held" looks like
     * from inside the database - rather than for a guessed amount of time.
     */
    private static void awaitBlockedOnLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
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
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch was never released");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
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

    private static RawOperation fullClaim(UUID idempotencyKey) {
        return new RawOperation(
                idempotencyKey,
                OperationStatus.IN_PROCESS,
                true,
                "raw-result",
                "raw-response",
                "fingerprint",
                null,
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private static RawOperation fullRewrite(UUID idempotencyKey) {
        return new RawOperation(
                idempotencyKey,
                OperationStatus.PROCESSED,
                false,
                "new-result",
                "new-response",
                "new-fingerprint",
                Duration.ofHours(48).toMillis(),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private static RawOperation completed(UUID idempotencyKey) {
        return new RawOperation(
                idempotencyKey,
                OperationStatus.PROCESSED,
                true,
                "new-result",
                null,
                "new-fingerprint",
                Duration.ofHours(48).toMillis(),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }
}
