package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NonBlockingOperationUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LOCK_TOKEN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = TestClock.EPOCH;
    private static final Long REMAINING_LOCK_MILLIS = 300_000L;
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);

    @Test
    @DisplayName("UT ofContext() should claim the key in process under the given lock token, leaving lease and creation time to the store")
    void ofContext_shouldClaimKeyInProcessUnderGivenLockTokenLeavingLeaseAndCreationTimeToStore() {
        // when
        NonBlockingOperation result = NonBlockingOperation.ofContext(context("fingerprint"), LOCK_TOKEN);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.getLockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.getRemainingLockMillis()).isNull();
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getCreatedAt()).isNull();
        assertThat(result.getResult()).isNull();
        assertThat(result.getRemainingMillis()).isNull();
    }

    @Test
    @DisplayName("UT ofConflict() should describe an attempt that met a held claim and got no record of its own")
    void ofConflict_shouldDescribeAttemptThatMetHeldClaimAndGotNoRecordOfItsOwn() {
        // when
        NonBlockingOperation result = NonBlockingOperation.ofConflict(context("fingerprint"));

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.CONFLICT);
        assertThat(result.hasConflict()).isTrue();
        assertThat(result.getLockToken()).isNull();
    }

    @Test
    @DisplayName("UT hasConflict() when the attempt met a held claim should report a conflict")
    void hasConflict_whenAttemptMetHeldClaim_shouldReportConflict() {
        assertThat(operation(OperationStatus.CONFLICT, null).hasConflict()).isTrue();
    }

    @Test
    @DisplayName("UT hasConflict() when the operation is running under this call's claim should report no conflict")
    void hasConflict_whenOperationIsRunningUnderThisCallsClaim_shouldReportNoConflict() {
        assertThat(operation(OperationStatus.IN_PROCESS, null).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("UT hasConflict() when the operation is already processed should report no conflict")
    void hasConflict_whenOperationIsAlreadyProcessed_shouldReportNoConflict() {
        assertThat(operation(OperationStatus.PROCESSED, 1L).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation is past its expiry should report it as expired")
    void isExpired_whenProcessedOperationIsPastItsExpiry_shouldReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, 0L).isExpired()).isTrue();
    }

    @Test
    @DisplayName("UT isExpired() when a kept failure is past its expiry should report it as expired")
    void isExpired_whenKeptFailureIsPastItsExpiry_shouldReportItAsExpired() {
        assertThat(operation(OperationStatus.EXCEPTIONALLY_PROCESSED, -1L).isExpired()).isTrue();
    }

    @Test
    @DisplayName("UT isExpired() when a kept failure has time left should not report it as expired")
    void isExpired_whenKeptFailureHasTimeLeft_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.EXCEPTIONALLY_PROCESSED, 1L).isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when the operation is still running should not report it as expired")
    void isExpired_whenOperationIsStillRunning_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.IN_PROCESS, null).isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT toString() should name the claim's lease without printing its token")
    void toString_shouldNameClaimsLeaseWithoutPrintingItsToken() {
        // when
        String result = operation(OperationStatus.IN_PROCESS, null).toString();

        // then
        assertThat(result).startsWith("NonBlockingOperation{");
        assertThat(result).contains("idempotencyKey=" + KEY, "status=IN_PROCESS", "remainingLockMillis=" + REMAINING_LOCK_MILLIS);
        assertThat(result).doesNotContain(LOCK_TOKEN.toString());
    }

    private NonBlockingOperation operation(OperationStatus status, Long remainingMillis) {
        return new NonBlockingOperation(
                KEY,
                status,
                true,
                null,
                RESULT_TYPE,
                null,
                "fingerprint",
                remainingMillis,
                NOW,
                LOCK_TOKEN,
                REMAINING_LOCK_MILLIS
        );
    }

    private OperationContext context(String fingerprint) {
        return new DefaultOperationContext(RESULT_TYPE, () -> null, KEY, fingerprint);
    }
}
