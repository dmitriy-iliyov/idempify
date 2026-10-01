package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.RawNonBlockingOperation;
import io.github.dmitriyiliyov.idempify.core.RawOperation;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
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
 * {@code NULL} in each of them. That is the invariant the row's shape rests on, not a defensive nicety. The
 * remaining times - of the record and of the claim - are not columns but what the statement computed from
 * them, and they are read as they came.
 */
class PostgreSqlRepositoryUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant CREATED_AT = Instant.parse("2026-08-09T12:00:00Z");
    private static final long REMAINING_MILLIS = 86_400_000L;
    private static final long REMAINING_LOCK_MILLIS = 300_000L;

    @Test
    @DisplayName("UT toRawOperation() should read every column of a completed operation")
    void toRawOperation_shouldReadEveryColumnOfCompletedOperation() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.PROCESSED, "raw-result", "raw-response", "fingerprint", REMAINING_MILLIS);

        // when
        RawOperation result = PostgreSqlRepositoryUtils.toRawOperation(rs);

        // then
        assertThat(result.idempotencyKey()).isEqualTo(KEY);
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.result()).isEqualTo("raw-result");
        assertThat(result.response()).isEqualTo("raw-response");
        assertThat(result.fingerprint()).isEqualTo("fingerprint");
        assertThat(result.remainingMillis()).isEqualTo(REMAINING_MILLIS);
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
        assertThat(result.remainingMillis()).isNull();
    }

    @Test
    @DisplayName("UT toRawNonBlockingOperation() should read the claim beside every column a blocking row has")
    void toRawNonBlockingOperation_shouldReadClaimBesideEveryColumnBlockingRowHas() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.PROCESSED, "raw-result", "raw-response", "fingerprint", REMAINING_MILLIS);
        when(rs.getObject("lock_token", UUID.class)).thenReturn(LOCK_TOKEN);
        when(rs.getObject("remaining_lock_millis", Long.class)).thenReturn(REMAINING_LOCK_MILLIS);

        // when
        RawNonBlockingOperation result = PostgreSqlRepositoryUtils.toRawNonBlockingOperation(rs);

        // then
        assertThat(result.idempotencyKey()).isEqualTo(KEY);
        assertThat(result.status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.result()).isEqualTo("raw-result");
        assertThat(result.response()).isEqualTo("raw-response");
        assertThat(result.remainingMillis()).isEqualTo(REMAINING_MILLIS);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.remainingLockMillis()).isEqualTo(REMAINING_LOCK_MILLIS);
    }

    @Test
    @DisplayName("UT toRawNonBlockingOperation() when nobody holds the key should read the claim as empty")
    void toRawNonBlockingOperation_whenNobodyHoldsKey_shouldReadClaimAsEmpty() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.PROCESSED, "raw-result", null, "fingerprint", REMAINING_MILLIS);

        // when
        RawNonBlockingOperation result = PostgreSqlRepositoryUtils.toRawNonBlockingOperation(rs);

        // then
        assertThat(result.lockToken()).isNull();
        assertThat(result.remainingLockMillis()).isNull();
    }

    @Test
    @DisplayName("UT toRawResponseContainer() should read the response, the fingerprint and the remaining time")
    void toRawResponseContainer_shouldReadResponseFingerprintAndRemainingTime() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.PROCESSED, "raw-result", "raw-response", "fingerprint", REMAINING_MILLIS);

        // when
        RawResponseContainer result = PostgreSqlRepositoryUtils.toRawResponseContainer(rs);

        // then
        assertThat(result.getResponse()).isEqualTo("raw-response");
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getRemainingMillis()).isEqualTo(REMAINING_MILLIS);
    }

    @Test
    @DisplayName("UT toRawResponseContainer() when the operation has not answered yet should read both empty")
    void toRawResponseContainer_whenOperationHasNotAnsweredYet_shouldReadBothEmpty() throws SQLException {
        // given
        ResultSet rs = row(OperationStatus.IN_PROCESS, null, null, "fingerprint", null);

        // when
        RawResponseContainer result = PostgreSqlRepositoryUtils.toRawResponseContainer(rs);

        // then
        assertThat(result.getResponse()).isNull();
        assertThat(result.getRemainingMillis()).isNull();
    }

    private static ResultSet row(OperationStatus status,
                                 String result,
                                 String response,
                                 String fingerprint,
                                 Long remainingMillis) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("idempotency_key", UUID.class)).thenReturn(KEY);
        when(rs.getString("status")).thenReturn(status.name());
        when(rs.getBoolean("is_first_attempt")).thenReturn(true);
        when(rs.getString("result")).thenReturn(result);
        when(rs.getString("response")).thenReturn(response);
        when(rs.getString("fingerprint")).thenReturn(fingerprint);
        when(rs.getObject("remaining_millis", Long.class)).thenReturn(remainingMillis);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(CREATED_AT));
        return rs;
    }
}
