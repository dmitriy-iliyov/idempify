package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PostgreSqlTransactionalOperationRepository implements TransactionalOperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlTransactionalOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public RawOperation saveIfAbsent(RawOperation operation) {
        return jdbcClient
                .sql("""
                    INSERT INTO idempotent_operations 
                        (idempotency_key, status, is_first_attempt, result, response, fingerprint, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(idempotency_key) 
                    DO UPDATE 
                        SET is_first_attempt = false
                    RETURNING *
                """)
                .params(
                        operation.idempotencyKey(),
                        operation.status().name(),
                        operation.isFirstAttempt(),
                        operation.result(),
                        operation.response(),
                        operation.fingerprint(),
                        Timestamp.from(operation.createdAt())
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
                            expires_at = ?,
                            created_at = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING *
                """)
                .params(
                        operation.status().name(),
                        operation.isFirstAttempt(),
                        operation.result(),
                        operation.response(),
                        operation.fingerprint(),
                        PostgreSqlRepositoryUtils.toTimestamp(operation.expiresAt()),
                        Timestamp.from(operation.createdAt()),
                        operation.idempotencyKey(),
                        onStatus.name()
                )
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationStatusMismatchException(operation.idempotencyKey(), onStatus));
    }

    @Override
    public RawOperation saveResultAndUpdateStatus(UUID idempotencyKey,
                                                  String result,
                                                  OperationStatus status,
                                                  Instant expiresAt,
                                                  OperationStatus onStatus) {
        return jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET result = ?, status = ?, expires_at = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING *
                """)
                .params(result, status.name(), Timestamp.from(expiresAt), idempotencyKey, onStatus.name())
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationStatusMismatchException(idempotencyKey, onStatus));
    }
}
