package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.BlockingOperationRepository;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Objects;
import java.util.UUID;

public class PostgreSqlBlockingOperationRepository implements BlockingOperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlBlockingOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public RawOperation saveIfAbsent(RawOperation operation) {
        return jdbcClient
                .sql("""
                    INSERT INTO idempotent_operations 
                        (idempotency_key, status, is_first_attempt, result, response, fingerprint)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(idempotency_key) 
                    DO UPDATE 
                        SET is_first_attempt = false
                    RETURNING *, %s
                """.formatted(PostgreSqlRepositoryUtils.REMAINING_MILLIS_COLUMN))
                .params(
                        operation.idempotencyKey(),
                        operation.status().name(),
                        operation.isFirstAttempt(),
                        operation.result(),
                        operation.response(),
                        operation.fingerprint()
                )
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawOperation(rs))
                .single();
    }

    @Override
    public RawOperation update(RawOperation operation, OperationStatus onStatus) {
        return jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET status = ?,
                            is_first_attempt = ?,
                            result = ?,
                            response = ?,
                            fingerprint = ?,
                            expires_at = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING *, %s
                """.formatted(PostgreSqlRepositoryUtils.REMAINING_MILLIS_COLUMN))
                .params(
                        operation.status().name(),
                        operation.isFirstAttempt(),
                        operation.result(),
                        operation.response(),
                        operation.fingerprint(),
                        null,
                        operation.idempotencyKey(),
                        onStatus.name()
                )
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationStatusMismatchException(operation.idempotencyKey(), onStatus));
    }

    @Override
    public void saveResultAndUpdateStatus(UUID idempotencyKey,
                                          String result,
                                          OperationStatus status,
                                          long ttlMillis,
                                          OperationStatus onStatus) {
        int updated = jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET result = ?, status = ?, expires_at = clock_timestamp() + (? * INTERVAL '1 millisecond')
                    WHERE idempotency_key = ? AND status = ?
                """)
                .params(result, status.name(), ttlMillis, idempotencyKey, onStatus.name())
                .update();

        if (updated == 0) throw new OperationStatusMismatchException(idempotencyKey, onStatus);
    }
}
