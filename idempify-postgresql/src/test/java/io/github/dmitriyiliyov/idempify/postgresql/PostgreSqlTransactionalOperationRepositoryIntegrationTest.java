package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guarantees this store owes the core are the ones a mock cannot have: the claim is atomic and
 * first-writer-wins, both updates are conditional on the row's current status, and the expiry exists on a
 * completed operation and nowhere else. All of it is judged against a real Postgres.
 * <p>
 * The repository deals in rows only - no serializer takes part here, so what is written is what comes back.
 * Where a statement is meant to carry the whole record, that is asserted on the record and not field by
 * field: a column added to the table and to {@link RawOperation} but left out of the statement passes every
 * hand-picked assertion there is.
 */
@Testcontainers
class PostgreSqlTransactionalOperationRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlTransactionalOperationRepository tested;
    private PostgreSqlOperationRepository reader;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
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
        tested = new PostgreSqlTransactionalOperationRepository(jdbcClient);
        reader = new PostgreSqlOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
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
        assertThat(result).isEqualTo(toClaim);
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
        tested.saveResultAndUpdateStatus(
                key, "original-result", OperationStatus.PROCESSED, anHourFromNow(), OperationStatus.IN_PROCESS);

        // when
        RawOperation result = tested.saveIfAbsent(claim(key));

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.result()).isEqualTo("original-result");
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is claimed should leave the expiry and the response empty")
    void saveIfAbsent_whenKeyIsClaimed_shouldLeaveExpiryAndResponseEmpty() {
        // given
        UUID key = UUID.randomUUID();

        // when
        RawOperation result = tested.saveIfAbsent(claim(key));

        // then
        assertThat(result.expiresAt()).isNull();
        assertThat(result.response()).isNull();
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
        assertThat(result).isEqualTo(rewritten);
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
        assertThat(reader.findByIdempotencyKey(key)).contains(claimed);
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
        tested.saveResultAndUpdateStatus(
                key, "stale-result", OperationStatus.PROCESSED, anHourAgo(), OperationStatus.IN_PROCESS);
        givenStoredResponse(key);

        // when
        RawOperation result = tested.update(claim(key), OperationStatus.PROCESSED);

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.expiresAt()).isNull();
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
        tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, anHourFromNow(), OperationStatus.IN_PROCESS);

        // then
        assertThat(reader.findByIdempotencyKey(key)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(stored.result()).isEqualTo("result");
        });
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() should be what puts an expiry on a row that had none")
    void saveResultAndUpdateStatus_shouldBeWhatPutsExpiryOnRowThatHadNone() {
        // given
        UUID key = UUID.randomUUID();
        assertThat(tested.saveIfAbsent(claim(key)).expiresAt()).isNull();
        Instant expiresAt = anHourFromNow();

        // when
        tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, expiresAt, OperationStatus.IN_PROCESS);

        // then
        assertThat(reader.findByIdempotencyKey(key))
                .hasValueSatisfying(stored -> assertThat(stored.expiresAt()).isEqualTo(expiresAt));
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() should not touch the response the row carries")
    void saveResultAndUpdateStatus_shouldNotTouchResponseRowCarries() {
        // given - the answer is written by its own update, after the operation committed
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        givenStoredResponse(key);

        // when
        tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, anHourFromNow(), OperationStatus.IN_PROCESS);

        // then
        assertThat(reader.findByIdempotencyKey(key))
                .hasValueSatisfying(stored -> assertThat(stored.response()).isEqualTo("raw-response"));
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the status does not match should throw and leave the row untouched")
    void saveResultAndUpdateStatus_whenStatusDoesNotMatch_shouldThrowAndLeaveRowUntouched() {
        // given
        UUID key = UUID.randomUUID();
        RawOperation claimed = tested.saveIfAbsent(claim(key));

        // when
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, anHourFromNow(), OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());

        // then
        assertThat(reader.findByIdempotencyKey(key)).contains(claimed);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the same key completes again should replace the previous expiry")
    void saveResultAndUpdateStatus_whenSameKeyCompletesAgain_shouldReplacePreviousExpiry() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(claim(key));
        tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, anHourFromNow(), OperationStatus.IN_PROCESS);
        tested.update(claim(key), OperationStatus.PROCESSED);
        Instant later = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

        // when
        tested.saveResultAndUpdateStatus(
                key, "result", OperationStatus.PROCESSED, later, OperationStatus.IN_PROCESS);

        // then
        assertThat(reader.findByIdempotencyKey(key))
                .hasValueSatisfying(stored -> assertThat(stored.expiresAt()).isEqualTo(later));
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when the row exists should return every column it holds")
    void findByIdempotencyKey_whenRowExists_shouldReturnEveryColumnItHolds() {
        // given
        UUID key = UUID.randomUUID();
        RawOperation claimed = tested.saveIfAbsent(claim(key));

        // when
        Optional<RawOperation> result = reader.findByIdempotencyKey(key);

        // then
        assertThat(result).contains(claimed);
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when no row is stored under the key should return an empty optional")
    void findByIdempotencyKey_whenNoRowIsStoredUnderKey_shouldReturnEmptyOptional() {
        // when / then
        assertThat(reader.findByIdempotencyKey(UUID.randomUUID())).isEmpty();
    }

    private void givenStoredResponse(UUID idempotencyKey) {
        jdbcClient.sql("UPDATE idempotent_operations SET response = ? WHERE idempotency_key = ?")
                .params("raw-response", idempotencyKey)
                .update();
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
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
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
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private static Instant anHourFromNow() {
        return Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant anHourAgo() {
        return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
    }
}
