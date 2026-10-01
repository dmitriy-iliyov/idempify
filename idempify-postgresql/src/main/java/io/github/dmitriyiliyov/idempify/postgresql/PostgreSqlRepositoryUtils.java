package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import io.github.dmitriyiliyov.idempify.core.response.DefaultRawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PostgreSqlRepositoryUtils {

    /**
     * How long a row stays replayable, counted by the database's own clock at the moment of the statement, so
     * that no instance compares the stored expiry against a clock of its own. {@code NULL} for a row still
     * running, which has no expiry.
     * <p>
     * {@code clock_timestamp()} rather than {@code now()}: the latter is the start of the transaction, and a
     * blocking claim shares its transaction with the whole business method.
     */
    public static final String REMAINING_MILLIS_COLUMN =
            "(EXTRACT(EPOCH FROM (expires_at - clock_timestamp())) * 1000)::bigint AS remaining_millis";

    /**
     * How long the claim on a row is still held, counted the same way and for the same reason as
     * {@link #REMAINING_MILLIS_COLUMN}: an instance whose clock runs ahead would otherwise take over a lease
     * that is still alive. {@code NULL} when nobody holds the row.
     */
    public static final String REMAINING_LOCK_MILLIS_COLUMN =
            "(EXTRACT(EPOCH FROM (lock_until - clock_timestamp())) * 1000)::bigint AS remaining_lock_millis";

    private PostgreSqlRepositoryUtils() { }

    public static RawOperation toRawOperation(ResultSet rs) throws SQLException {
        return new RawOperation(
                rs.getObject("idempotency_key", UUID.class),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_first_attempt"),
                rs.getString("result"),
                rs.getString("response"),
                rs.getString("fingerprint"),
                rs.getObject("remaining_millis", Long.class),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public static RawNonBlockingOperation toRawNonBlockingOperation(ResultSet rs) throws SQLException {
        return new RawNonBlockingOperation(
                rs.getObject("idempotency_key", UUID.class),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_first_attempt"),
                rs.getString("result"),
                rs.getString("exception_class_name"),
                rs.getString("response"),
                rs.getString("fingerprint"),
                rs.getObject("remaining_millis", Long.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("lock_token", UUID.class),
                rs.getObject("remaining_lock_millis", Long.class)
        );
    }

    public static RawResponseContainer toRawResponseContainer(ResultSet rs) throws SQLException {
        return new DefaultRawResponseContainer(
                rs.getString("response"),
                rs.getString("fingerprint"),
                rs.getObject("remaining_millis", Long.class)
        );
    }
}
