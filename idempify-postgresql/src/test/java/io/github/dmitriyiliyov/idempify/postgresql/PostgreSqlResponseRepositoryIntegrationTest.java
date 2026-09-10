package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
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
 * The answer is written onto the row the operation already occupies, so the write is conditional on that
 * operation having completed: a row still running, or none at all, must not collect somebody's answer. The
 * read hands back the expiry as well, because that is what a cache in front of this store counts its own
 * lifetime from.
 */
@Testcontainers
class PostgreSqlResponseRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlResponseRepository tested;
    private PostgreSqlTransactionalOperationRepository operations;

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
        tested = new PostgreSqlResponseRepository(jdbcClient);
        operations = new PostgreSqlTransactionalOperationRepository(jdbcClient);
        jdbcClient.sql("DELETE FROM idempotent_operations").update();
    }

    @Test
    @DisplayName("IT save() when the operation has completed should write the answer onto its row")
    void save_whenOperationHasCompleted_shouldWriteAnswerOntoItsRow() {
        // given
        UUID key = completedOperation();

        // when
        RawResponseContainer result = tested.save(key, "raw-response");

        // then
        assertThat(result.getResponse()).isEqualTo("raw-response");
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("IT save() should hand back the record as it now stands so a cache can bound its own copy")
    void save_shouldHandBackRecordAsItNowStandsSoCacheCanBoundItsOwnCopy() {
        // given
        UUID key = completedOperation();
        Instant expiresAt = storedExpiry(key);

        // when
        RawResponseContainer result = tested.save(key, "raw-response");

        // then
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("IT save() when the operation is still running should throw and leave the row without an answer")
    void save_whenOperationIsStillRunning_shouldThrowAndLeaveRowWithoutAnswer() {
        // given
        UUID key = UUID.randomUUID();
        operations.saveIfAbsent(claim(key));

        // when / then
        assertThatThrownBy(() -> tested.save(key, "raw-response"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key.toString());
        assertThat(tested.findByIdempotencyKey(key))
                .hasValueSatisfying(container -> assertThat(container.getResponse()).isNull());
    }

    @Test
    @DisplayName("IT save() when no row is stored under the key should throw")
    void save_whenNoRowIsStoredUnderKey_shouldThrow() {
        // given
        UUID key = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> tested.save(key, "raw-response"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(key.toString());
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when the answer was written should read it back with its expiry")
    void findByIdempotencyKey_whenAnswerWasWritten_shouldReadItBackWithItsExpiry() {
        // given
        UUID key = completedOperation();
        RawResponseContainer saved = tested.save(key, "raw-response");

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(key);

        // then
        assertThat(result).contains(saved);
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when the operation has not answered yet should read the record with an empty response")
    void findByIdempotencyKey_whenOperationHasNotAnsweredYet_shouldReadRecordWithEmptyResponse() {
        // given
        UUID key = UUID.randomUUID();
        operations.saveIfAbsent(claim(key));

        // when
        Optional<RawResponseContainer> result = tested.findByIdempotencyKey(key);

        // then
        assertThat(result).hasValueSatisfying(container -> {
            assertThat(container.getResponse()).isNull();
            assertThat(container.getExpiresAt()).isNull();
            assertThat(container.getFingerprint()).isEqualTo("fingerprint");
        });
    }

    @Test
    @DisplayName("IT findByIdempotencyKey() when no row is stored under the key should return an empty optional")
    void findByIdempotencyKey_whenNoRowIsStoredUnderKey_shouldReturnEmptyOptional() {
        // when / then
        assertThat(tested.findByIdempotencyKey(UUID.randomUUID())).isEmpty();
    }

    private UUID completedOperation() {
        UUID key = UUID.randomUUID();
        operations.saveIfAbsent(claim(key));
        operations.saveResultAndUpdateStatus(
                key,
                "result",
                OperationStatus.PROCESSED,
                Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                OperationStatus.IN_PROCESS
        );
        return key;
    }

    private Instant storedExpiry(UUID idempotencyKey) {
        return new PostgreSqlOperationRepository(jdbcClient)
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow()
                .expiresAt();
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
}
