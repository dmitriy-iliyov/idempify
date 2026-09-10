package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgreSqlOperationRepository implements OperationRepository {

    private final JdbcClient jdbcClient;

    public PostgreSqlOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Override
    public Optional<RawOperation> findByIdempotencyKey(UUID idempotencyKey) {
        return jdbcClient
                .sql("""
                    SELECT * FROM idempotent_operations
                    WHERE idempotency_key = ?
                """)
                .param(idempotencyKey)
                .query((rs, rowNum) -> PostgreSqlRepositoryUtils.toRawOperation(rs))
                .optional();
    }
}
