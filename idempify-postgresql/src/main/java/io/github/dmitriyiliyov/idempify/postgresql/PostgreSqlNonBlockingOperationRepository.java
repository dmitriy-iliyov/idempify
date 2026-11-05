package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.NonBlockingOperationRepository;
import io.github.dmitriyiliyov.idempify.core.OperationLockLostException;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PostgreSqlNonBlockingOperationRepository implements NonBlockingOperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlNonBlockingOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Transactional
    @Override
    public boolean lockOrSaveAndLock(RawNonBlockingOperation operation, long lockTtlMillis) {
        String sql = """
            INSERT INTO idempotent_operations
                (idempotency_key, status, is_first_attempt, result, exception_class_name, response, fingerprint,
                lock_token, lock_until)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp() + (? * INTERVAL '1 millisecond'))
            ON CONFLICT(idempotency_key)
            DO UPDATE
                SET lock_token = EXCLUDED.lock_token,
                    lock_until = EXCLUDED.lock_until
                WHERE idempotent_operations.lock_token IS NULL
                    OR idempotent_operations.lock_token = EXCLUDED.lock_token
                    OR idempotent_operations.lock_until <= clock_timestamp()
            RETURNING lock_token
        """;
        return jdbcClient.sql(sql)
                .params(
                        operation.idempotencyKey(),
                        operation.status().name(),
                        operation.isFirstAttempt(),
                        operation.result(),
                        operation.exceptionClassName(),
                        operation.response(),
                        operation.fingerprint(),
                        operation.lockToken(),
                        lockTtlMillis
                )
                .query((rs, rowNum) -> rs.getObject("lock_token", UUID.class))
                .optional()
                .isPresent();
    }

    @Transactional
    @Override
    public Optional<RawNonBlockingOperation> find(UUID idempotencyKey) {
        return jdbcClient
                .sql("""
                    SELECT *, %s, %s
                    FROM idempotent_operations
                    WHERE idempotency_key = ?
                """.formatted(
                        PostgreSqlRepositoryUtils.REMAINING_MILLIS_COLUMN,
                        PostgreSqlRepositoryUtils.REMAINING_LOCK_MILLIS_COLUMN
                ))
                .param(idempotencyKey)
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawNonBlockingOperation(rs))
                .optional();
    }

    @Transactional
    @Override
    public RawNonBlockingOperation updateByLockToken(RawNonBlockingOperation operation,
                                                     Set<OperationStatus> onStatuses) {
        String sql = """
            UPDATE idempotent_operations
                SET status = :status,
                    is_first_attempt = :isFirstAttempt,
                    result = NULL,
                    exception_class_name = NULL,
                    response = NULL,
                    fingerprint = :fingerprint,
                    expires_at = NULL
            WHERE idempotency_key = :idempotencyKey AND lock_token = :lockToken AND status IN (:onStatuses)
            RETURNING *, %s, %s
        """.formatted(
                PostgreSqlRepositoryUtils.REMAINING_MILLIS_COLUMN,
                PostgreSqlRepositoryUtils.REMAINING_LOCK_MILLIS_COLUMN
        );
        return jdbcClient.sql(sql)
                .param("idempotencyKey", operation.idempotencyKey())
                .param("status", operation.status().name())
                .param("isFirstAttempt", operation.isFirstAttempt())
                .param("fingerprint", operation.fingerprint())
                .param("lockToken", operation.lockToken())
                .param("onStatuses", onStatuses.stream().map(OperationStatus::name).toList())
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawNonBlockingOperation(rs))
                .optional()
                .orElseThrow(() -> new OperationLockLostException(operation.idempotencyKey()));
    }

    @Transactional
    @Override
    public void saveResultAndUpdateStatusByLockToken(UUID idempotencyKey,
                                                     String result,
                                                     OperationStatus newStatus,
                                                     long ttlMillis,
                                                     UUID lockToken,
                                                     OperationStatus onStatus) {
        String sql = """
            UPDATE idempotent_operations
                SET status = ?, 
                    result = ?, 
                    expires_at = clock_timestamp() + (? * INTERVAL '1 millisecond')
            WHERE idempotency_key = ? AND status = ? AND lock_token = ?
        """;
        int updated = jdbcClient.sql(sql)
                .params(newStatus.name(), result, ttlMillis, idempotencyKey, onStatus.name(), lockToken)
                .update();
        if (updated != 1) {
            throw new OperationLockLostException(idempotencyKey);
        }
    }

    @Transactional
    @Override
    public void saveExceptionAndUpdateStatusByLockToken(UUID idempotencyKey,
                                                        String exception,
                                                        ResultType type,
                                                        OperationStatus newStatus,
                                                        long ttlMillis,
                                                        UUID lockToken,
                                                        OperationStatus onStatus) {
        String sql = """
            UPDATE idempotent_operations
                SET status = ?,
                    exception_class_name = ?,
                    result = ?,
                    expires_at = clock_timestamp() + (? * INTERVAL '1 millisecond')
            WHERE idempotency_key = ? AND status = ? AND lock_token = ?
        """;
        int updated = jdbcClient.sql(sql)
                .params(
                        newStatus.name(),
                        type.getType().getTypeName(),
                        exception,
                        ttlMillis,
                        idempotencyKey,
                        onStatus.name(),
                        lockToken
                )
                .update();
        if (updated != 1) {
            throw new OperationLockLostException(idempotencyKey);
        }
    }

    @Transactional
    @Override
    public void deleteByLockToken(UUID idempotencyKey, UUID lockToken) {
        String sql = """
            DELETE FROM idempotent_operations
            WHERE idempotency_key = ? AND lock_token = ?
        """;
        int deleted = jdbcClient.sql(sql)
                .params(idempotencyKey, lockToken)
                .update();
        if (deleted != 1) {
            throw new OperationLockLostException(idempotencyKey);
        }
    }

    @Transactional
    @Override
    public void unlock(UUID idempotencyKey, UUID lockToken) {
        String sql = """
            UPDATE idempotent_operations
                SET lock_token = NULL, lock_until = NULL
            WHERE idempotency_key = ? AND lock_token = ?
        """;
        jdbcClient.sql(sql)
                .params(idempotencyKey, lockToken)
                .update();
    }
}
