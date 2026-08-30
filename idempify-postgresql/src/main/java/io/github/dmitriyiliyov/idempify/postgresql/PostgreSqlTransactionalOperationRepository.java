package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.Operation;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.OperationStatusMismatchException;
import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgreSqlTransactionalOperationRepository implements TransactionalOperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlTransactionalOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public Operation saveIfAbsent(Operation operation) {
        return jdbcClient
                .sql("""
                    INSERT INTO idempotent_operations (idempotency_key, status, is_first_attempt, result, fingerprint, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(idempotency_key) 
                    DO UPDATE 
                        SET is_first_attempt = false
                    RETURNING *
                """)
                .params(
                        operation.getIdempotencyKey(),
                        operation.getStatus().name(),
                        operation.isFirstAttempt(),
                        operation.getResult(),
                        operation.getFingerprint(),
                        Timestamp.from(operation.getExpiresAt()),
                        Timestamp.from(operation.getCreatedAt())
                )
                .query((rs, rowNum) -> toOperation(rs))
                .single();
    }

    @Override
    public Operation update(Operation operation, OperationStatus onStatus) {
        return jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET status = ?,
                            is_first_attempt = ?,
                            result = ?,
                            fingerprint = ?,
                            expires_at = ?,
                            created_at = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING *
                """)
                .params(
                        operation.getStatus().name(),
                        operation.isFirstAttempt(),
                        operation.getResult(),
                        operation.getFingerprint(),
                        Timestamp.from(operation.getExpiresAt()),
                        Timestamp.from(operation.getCreatedAt()),
                        operation.getIdempotencyKey(),
                        onStatus.name()
                )
                .query((rs, rowNum) -> toOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationStatusMismatchException(operation.getIdempotencyKey(), onStatus));
    }

    @Override
    public Operation saveResultAndUpdateStatus(String result, OperationStatus status, UUID idempotencyKey, OperationStatus onStatus) {
        return jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET result = ?, status = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING *
                """)
                .params(result, status.name(), idempotencyKey, onStatus.name())
                .query((rs, rowNum) -> toOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationStatusMismatchException(idempotencyKey, onStatus));
    }

    @Override
    public Optional<Operation> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbcClient
                .sql("""
                    SELECT * FROM idempotent_operations
                    WHERE idempotency_key = ?
                """)
                .param(idempotencyKey)
                .query((rs, rowNum) -> toOperation(rs))
                .optional();
    }

    private Operation toOperation(ResultSet rs) throws SQLException {
        return new Operation(
                rs.getObject("idempotency_key", UUID.class),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_first_attempt"),
                rs.getString("result"),
                rs.getString("fingerprint"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
