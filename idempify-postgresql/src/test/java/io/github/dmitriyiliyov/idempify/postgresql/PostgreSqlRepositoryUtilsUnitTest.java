package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A claim leaves three columns empty - no result, no response, no expiry - so reading a row has to survive
 * {@code NULL} in each of them. That is the invariant the row's shape rests on, not a defensive nicety.
 */
class PostgreSqlRepositoryUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant CREATED_AT = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    @DisplayName("UT toRawOperation() should read every column of a completed operation")
    void toRawOperation_shouldReadEveryColumnOfCompletedOperation() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.PROCESSED, "raw-result", "raw-response", "fingerprint",
                Timestamp.from(EXPIRES_AT));

        // when
        RawOperation result = PostgreSqlRepositoryUtils.toRawOperation(rs);

        // then
        assertThat(result.idempotencyKey()).isEqualTo(KEY);
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.result()).isEqualTo("raw-result");
        assertThat(result.response()).isEqualTo("raw-response");
        assertThat(result.fingerprint()).isEqualTo("fingerprint");
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("UT toRawOperation() when the row is a claim should read it with its columns still empty")
    void toRawOperation_whenRowIsClaim_shouldReadItWithColumnsStillEmpty() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.IN_PROCESS, null, null, null, null);

        // when
        RawOperation result = PostgreSqlRepositoryUtils.toRawOperation(rs);

        // then
        assertThat(result.status()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.result()).isNull();
        assertThat(result.response()).isNull();
        assertThat(result.fingerprint()).isNull();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("UT toInstant() when the timestamp is null should hand back null")
    void toInstant_whenTimestampIsNull_shouldHandBackNull() {
        // when / then
        assertThat(PostgreSqlRepositoryUtils.toInstant(null)).isNull();
    }

    @Test
    @DisplayName("UT toTimestamp() when the instant is null should hand back null")
    void toTimestamp_whenInstantIsNull_shouldHandBackNull() {
        // when / then
        assertThat(PostgreSqlRepositoryUtils.toTimestamp(null)).isNull();
    }

    @Test
    @DisplayName("UT toTimestamp() then toInstant() should hand back the instant it was given")
    void toTimestamp_thenToInstant_shouldHandBackInstantItWasGiven() {
        // when / then
        assertThat(PostgreSqlRepositoryUtils.toInstant(PostgreSqlRepositoryUtils.toTimestamp(EXPIRES_AT)))
                .isEqualTo(EXPIRES_AT);
    }

    private static ResultSet row(OperationStatus status,
                                 String result,
                                 String response,
                                 String fingerprint,
                                 Timestamp expiresAt) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("idempotency_key", UUID.class)).thenReturn(KEY);
        when(rs.getString("status")).thenReturn(status.name());
        when(rs.getBoolean("is_first_attempt")).thenReturn(true);
        when(rs.getString("result")).thenReturn(result);
        when(rs.getString("result_type")).thenReturn("raw-type");
        when(rs.getString("response")).thenReturn(response);
        when(rs.getString("fingerprint")).thenReturn(fingerprint);
        when(rs.getTimestamp("expires_at")).thenReturn(expiresAt);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(CREATED_AT));
        return rs;
    }
}
