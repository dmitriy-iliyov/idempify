package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgreSqlResponseRepository implements ResponseRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlResponseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public RawResponseContainer save(UUID idempotencyKey, String response) {
        Optional<RawResponseContainer> updated = jdbcClient
                .sql("""
                    UPDATE idempotent_operations
                    SET response = ?
                    WHERE idempotency_key = ? AND status = ?
                    RETURNING response, fingerprint, expires_at
                """)
                .params(response, idempotencyKey, OperationStatus.PROCESSED.name())
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawResponseContainer(rs))
                .optional();

        if (updated.isEmpty()) {
            throw new IllegalStateException(
                    "Operation (idempotencyKey=%s) has no completed record to save a response onto".formatted(idempotencyKey)
            );
        }

        return updated.get();
    }

    @Override
    public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbcClient
                .sql("""
                    SELECT response, fingerprint, expires_at
                    FROM idempotent_operations
                    WHERE idempotency_key = ?
                """)
                .params(idempotencyKey)
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawResponseContainer(rs))
                .optional();
    }
}
