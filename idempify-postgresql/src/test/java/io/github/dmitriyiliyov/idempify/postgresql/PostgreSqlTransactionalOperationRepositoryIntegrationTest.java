package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.Operation;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
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

@Testcontainers
class PostgreSqlTransactionalOperationRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlTransactionalOperationRepository tested;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        try {
            jdbcClient.sql(new ClassPathResource("idempotent_operations_table.sql").getContentAsString(StandardCharsets.UTF_8)).update();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        tested = new PostgreSqlTransactionalOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when key does not exist should insert and return operation with isFirstAttempt true")
    void saveIfAbsent_whenKeyDoesNotExist_shouldInsertAndReturnWithIsFirstAttemptTrue() {
        // given
        UUID key = UUID.randomUUID();
        Operation operation = buildOperation(key);

        // when
        Operation result = tested.saveIfAbsent(operation);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isNull();
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("IT saveIfAbsent() when key already exists should return existing operation with isFirstAttempt false")
    void saveIfAbsent_whenKeyAlreadyExists_shouldReturnExistingWithIsFirstAttemptFalse() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        // when
        Operation result = tested.saveIfAbsent(buildOperation(key));

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
        assertThat(result.isFirstAttempt()).isFalse();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when key already exists should not overwrite existing status and result")
    void saveIfAbsent_whenKeyAlreadyExists_shouldNotOverwriteExistingStatusAndResult() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        tested.saveResultAndUpdateStatus("original-result", OperationStatus.PROCESSED, anHourFromNow(), key, OperationStatus.IN_PROCESS);

        // when
        Operation result = tested.saveIfAbsent(buildOperation(key));

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("original-result");
        assertThat(result.isFirstAttempt()).isFalse();
    }

    @Test
    @DisplayName("IT update() when status matches onStatus should update all fields and return updated operation")
    void update_whenStatusMatchesOnStatus_shouldUpdateAndReturnUpdatedOperation() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        Operation toUpdate = new Operation(
                key,
                OperationStatus.PROCESSED,
                true,
                "result-payload",
                "fp-hash",
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        // when
        Operation result = tested.update(toUpdate, OperationStatus.IN_PROCESS);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("result-payload");
        assertThat(result.getFingerprint()).isEqualTo("fp-hash");
    }

    @Test
    @DisplayName("IT update() when status does not match onStatus should throw OperationStatusMismatchException")
    void update_whenStatusDoesNotMatchOnStatus_shouldThrowOperationStatusMismatchException() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        Operation toUpdate = updateFor(key);

        // when / then
        assertThatThrownBy(() -> tested.update(toUpdate, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());
    }

    @Test
    @DisplayName("IT update() when status does not match onStatus should leave the stored operation alone")
    void update_whenStatusDoesNotMatchOnStatus_shouldLeaveStoredOperationAlone() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        Operation toUpdate = updateFor(key);

        // when
        assertThatThrownBy(() -> tested.update(toUpdate, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class);

        // then
        assertThat(tested.findByIdempotencyKey(key)).hasValueSatisfying(stored -> {
            assertThat(stored.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(stored.getResult()).isNull();
            assertThat(stored.getFingerprint()).isEqualTo("fingerprint");
        });
    }

    @Test
    @DisplayName("IT update() when no operation is stored under the key should throw OperationStatusMismatchException")
    void update_whenNoOperationIsStoredUnderKey_shouldThrowOperationStatusMismatchException() {
        // given
        UUID key = UUID.randomUUID();
        Operation toUpdate = updateFor(key);

        // when / then
        assertThatThrownBy(() -> tested.update(toUpdate, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString());
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when status matches onStatus should update result and status")
    void saveResultAndUpdateStatus_whenStatusMatchesOnStatus_shouldUpdateResultAndStatus() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        String result = "serialized-result";

        // when
        tested.saveResultAndUpdateStatus(result, OperationStatus.PROCESSED, anHourFromNow(), key, OperationStatus.IN_PROCESS);

        // then
        Optional<Operation> updated = tested.findByIdempotencyKey(key);
        assertThat(updated).isPresent();
        assertThat(updated.get().getResult()).isEqualTo(result);
        assertThat(updated.get().getStatus()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when status does not match onStatus should throw and leave the operation untouched")
    void saveResultAndUpdateStatus_whenStatusDoesNotMatchOnStatus_shouldThrowAndLeaveOperationUntouched() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        // when
        assertThatThrownBy(() -> tested.saveResultAndUpdateStatus("new-result", OperationStatus.PROCESSED, anHourFromNow(), key, OperationStatus.PROCESSED))
                .isInstanceOf(OperationStatusMismatchException.class)
                .hasMessageContaining(key.toString())
                .hasMessageContaining(OperationStatus.PROCESSED.name());

        // then
        Optional<Operation> notUpdated = tested.findByIdempotencyKey(key);
        assertThat(notUpdated).isPresent();
        assertThat(notUpdated.get().getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(notUpdated.get().getResult()).isNull();
    }

    @Test
    @DisplayName("IT saveIfAbsent() when the key is claimed should store a row that carries no expiry")
    void saveIfAbsent_whenKeyIsClaimed_shouldStoreRowThatCarriesNoExpiry() {
        // given
        UUID key = UUID.randomUUID();

        // when
        Operation claimed = tested.saveIfAbsent(buildOperation(key));

        // then
        assertThat(claimed.getExpiresAt()).isNull();
        assertThat(tested.findByIdempotencyKey(key)).hasValueSatisfying(
                stored -> assertThat(stored.getExpiresAt()).isNull());
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() should be what puts an expiry on a row that had none")
    void saveResultAndUpdateStatus_shouldBeWhatPutsExpiryOnRowThatHadNone() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        Instant expiresAt = anHourFromNow();

        // when
        Operation completed = tested.saveResultAndUpdateStatus(
                "serialized-result", OperationStatus.PROCESSED, expiresAt, key, OperationStatus.IN_PROCESS);

        // then
        assertThat(completed.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(tested.findByIdempotencyKey(key)).hasValueSatisfying(
                stored -> assertThat(stored.getExpiresAt()).isEqualTo(expiresAt));
    }

    @Test
    @DisplayName("IT saveResultAndUpdateStatus() when the same key completes again should replace the previous expiry")
    void saveResultAndUpdateStatus_whenSameKeyCompletesAgain_shouldReplacePreviousExpiry() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        Instant firstExpiry = anHourFromNow();
        tested.saveResultAndUpdateStatus("first", OperationStatus.PROCESSED, firstExpiry, key, OperationStatus.IN_PROCESS);
        Instant secondExpiry = Instant.now().plus(9, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

        // when
        tested.saveResultAndUpdateStatus("second", OperationStatus.PROCESSED, secondExpiry, key, OperationStatus.PROCESSED);

        // then
        assertThat(tested.findByIdempotencyKey(key)).hasValueSatisfying(
                stored -> assertThat(stored.getExpiresAt()).isEqualTo(secondExpiry));
    }

    @Test
    @DisplayName("IT update() when an expired row is reclaimed should clear the expiry it inherited")
    void update_whenExpiredRowIsReclaimed_shouldClearExpiryItInherited() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        Instant staleExpiry = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        tested.saveResultAndUpdateStatus("stale", OperationStatus.PROCESSED, staleExpiry, key, OperationStatus.IN_PROCESS);

        // when
        Operation reclaimed = tested.update(buildOperation(key), OperationStatus.PROCESSED);

        // then
        assertThat(reclaimed.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(reclaimed.getExpiresAt()).isNull();
        assertThat(reclaimed.getResult()).isNull();
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when operation exists should return optional with operation")
    void findByIdempotencyKey_whenOperationExists_shouldReturnOptionalWithOperation() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        // when
        Optional<Operation> result = tested.findByIdempotencyKey(key);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo(key);
        assertThat(result.get().getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when operation does not exist should return empty optional")
    void findByIdempotencyKey_whenOperationDoesNotExist_shouldReturnEmptyOptional() {
        // given
        UUID nonExistentKey = UUID.randomUUID();

        // when
        Optional<Operation> result = tested.findByIdempotencyKey(nonExistentKey);

        // then
        assertThat(result).isEmpty();
    }

    private Operation updateFor(UUID key) {
        return new Operation(
                key,
                OperationStatus.PROCESSED,
                true,
                "new-result",
                "new-fp",
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private Operation buildOperation(UUID key) {
        return new Operation(
                key,
                OperationStatus.IN_PROCESS,
                true,
                null,
                "fingerprint",
                null,
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private Instant anHourFromNow() {
        return Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
    }
}