package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.Operation;
import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.OperationState;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgreSqlOperationRepository implements OperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public Operation saveIfAbsent(Operation operation) {
        return jdbcClient
                .sql("""
                    INSERT INTO idempotent_operations (idempotency_key, state, is_first_attempt, result, fingerprint, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(idempotency_key) 
                    DO UPDATE 
                        SET is_first_attempt = false
                    RETURNING *
                """)
                .params(
                        operation.getIdempotencyKey(),
                        operation.getState().name(),
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
    public Operation update(Operation operation, OperationState onState) {
        return jdbcClient
                .sql("""
                    WITH updated AS (
                        UPDATE idempotent_operations
                        SET state = ?,
                            is_first_attempt = ?,
                            result = ?,
                            fingerprint = ?,
                            expires_at = ?,
                            created_at = ?
                        WHERE idempotency_key = ? AND state = ?
                        RETURNING *
                    )
                    SELECT * FROM updated
                    UNION ALL
                    SELECT * FROM idempotent_operations
                    WHERE idempotency_key = ?
                      AND NOT EXISTS (SELECT 1 FROM updated)
                """)
                .params(
                        operation.getState().name(),
                        operation.isFirstAttempt(),
                        operation.getResult(),
                        operation.getFingerprint(),
                        Timestamp.from(operation.getExpiresAt()),
                        Timestamp.from(operation.getCreatedAt()),
                        operation.getIdempotencyKey(),
                        onState.name(),
                        operation.getIdempotencyKey()
                )
                .query((rs, rowNum) -> toOperation(rs))
                .single();
    }

    @Override
    public void saveResultAndUpdateState(String result, OperationState state, UUID idempotencyKey, OperationState onState) {
        jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                        SET result = ?, state = ?
                        WHERE idempotency_key = ? AND state = ?
                """)
                .params(result, state.name(), idempotencyKey, onState.name())
                .update();
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
                OperationState.valueOf(rs.getString("state")),
                rs.getBoolean("is_first_attempt"),
                rs.getString("result"),
                rs.getString("fingerprint"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
