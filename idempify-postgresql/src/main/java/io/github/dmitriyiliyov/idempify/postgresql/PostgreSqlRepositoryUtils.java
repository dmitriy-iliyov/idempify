package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class PostgreSqlRepositoryUtils {

    private PostgreSqlRepositoryUtils() { }

    public static RawOperation toRawOperation(ResultSet rs) throws SQLException {
        return new RawOperation(
                rs.getObject("idempotency_key", UUID.class),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_first_attempt"),
                rs.getString("result"),
                rs.getString("response"),
                rs.getString("fingerprint"),
                toInstant(rs.getTimestamp("expires_at")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public static RawResponseContainer toRawResponseContainer(ResultSet rs) throws SQLException {
        return new DefaultRawResponseContainer(
                rs.getString("response"),
                rs.getString("fingerprint"),
                PostgreSqlRepositoryUtils.toInstant(rs.getTimestamp("expires_at"))
        );
    }

    public static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
