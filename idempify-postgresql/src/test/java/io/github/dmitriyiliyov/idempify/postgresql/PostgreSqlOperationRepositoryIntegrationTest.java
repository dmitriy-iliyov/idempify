package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.Operation;
import io.github.dmitriyiliyov.idempify.core.OperationState;
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
import static org.junit.Assert.assertEquals;

@Testcontainers
class PostgreSqlOperationRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbcClient;
    private PostgreSqlOperationRepository tested;

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
        tested = new PostgreSqlOperationRepository(jdbcClient);
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
        assertThat(result.getState()).isEqualTo(OperationState.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isNull();
        assertEquals("fingerprint", result.getFingerprint());
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
    @DisplayName("IT saveIfAbsent() when key already exists should not overwrite existing state and result")
    void saveIfAbsent_whenKeyAlreadyExists_shouldNotOverwriteExistingStateAndResult() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        tested.saveResultAndUpdateState("original-result", OperationState.PROCESSED, key, OperationState.IN_PROCESS);

        // when
        Operation result = tested.saveIfAbsent(buildOperation(key));

        // then
        assertThat(result.getState()).isEqualTo(OperationState.PROCESSED);
        assertThat(result.getResult()).isEqualTo("original-result");
        assertThat(result.isFirstAttempt()).isFalse();
    }

    @Test
    @DisplayName("IT update() when state matches onState should update all fields and return updated operation")
    void update_whenStateMatchesOnState_shouldUpdateAndReturnUpdatedOperation() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        Operation toUpdate = new Operation(
                key,
                OperationState.PROCESSED,
                true,
                "result-payload",
                "fp-hash",
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        // when
        Operation result = tested.update(toUpdate, OperationState.IN_PROCESS);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
        assertThat(result.getState()).isEqualTo(OperationState.PROCESSED);
        assertThat(result.getResult()).isEqualTo("result-payload");
        assertThat(result.getFingerprint()).isEqualTo("fp-hash");
    }

    @Test
    @DisplayName("IT update() when state does not match onState should not update and return current operation")
    void update_whenStateDoesNotMatchOnState_shouldNotUpdateAndReturnCurrentOperation() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        Operation toUpdate = new Operation(
                key,
                OperationState.PROCESSED,
                true,
                "new-result",
                "new-fp",
                Instant.now().plus(48, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        // when
        Operation result = tested.update(toUpdate, OperationState.PROCESSED);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(key);
        assertThat(result.getState()).isEqualTo(OperationState.IN_PROCESS);
        assertThat(result.getResult()).isNull();
        assertEquals("fingerprint", result.getFingerprint());
    }

    @Test
    @DisplayName("IT saveResultAndUpdateState() when state matches onState should update result and state")
    void saveResultAndUpdateState_whenStateMatchesOnState_shouldUpdateResultAndState() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));
        String result = "serialized-result";

        // when
        tested.saveResultAndUpdateState(result, OperationState.PROCESSED, key, OperationState.IN_PROCESS);

        // then
        Optional<Operation> updated = tested.findByIdempotencyKey(key);
        assertThat(updated).isPresent();
        assertThat(updated.get().getResult()).isEqualTo(result);
        assertThat(updated.get().getState()).isEqualTo(OperationState.PROCESSED);
    }

    @Test
    @DisplayName("IT saveResultAndUpdateState() when state does not match onState should not update")
    void saveResultAndUpdateState_whenStateDoesNotMatchOnState_shouldNotUpdate() {
        // given
        UUID key = UUID.randomUUID();
        tested.saveIfAbsent(buildOperation(key));

        // when
        tested.saveResultAndUpdateState("new-result", OperationState.PROCESSED, key, OperationState.PROCESSED);

        // then
        Optional<Operation> notUpdated = tested.findByIdempotencyKey(key);
        assertThat(notUpdated).isPresent();
        assertThat(notUpdated.get().getState()).isEqualTo(OperationState.IN_PROCESS);
        assertThat(notUpdated.get().getResult()).isNull();
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
        assertThat(result.get().getState()).isEqualTo(OperationState.IN_PROCESS);
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

    private Operation buildOperation(UUID key) {
        return new Operation(
                key,
                OperationState.IN_PROCESS,
                true,
                null,
                "fingerprint",
                Instant.now().plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS),
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
    }
}