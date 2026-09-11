package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.response.DefaultResponse;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = TestClock.EPOCH;
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final ResultType OTHER_RESULT_TYPE = ResultType.ofClass(Integer.class);
    private static final Response RESPONSE = new DefaultResponse(201, new byte [0], "application/json", Map.of());

    @Test
    @DisplayName("UT hasConflict() when the operation is running and this is not the first attempt should report a conflict")
    void hasConflict_whenOperationIsRunningAndThisIsNotFirstAttempt_shouldReportConflict() {
        assertThat(operation(OperationStatus.IN_PROCESS, false, NOW).hasConflict()).isTrue();
    }

    @Test
    @DisplayName("UT hasConflict() when the operation is running on its first attempt should report no conflict")
    void hasConflict_whenOperationIsRunningOnFirstAttempt_shouldReportNoConflict() {
        assertThat(operation(OperationStatus.IN_PROCESS, true, NOW).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("UT hasConflict() when the operation is already processed should report no conflict")
    void hasConflict_whenOperationIsAlreadyProcessed_shouldReportNoConflict() {
        assertThat(operation(OperationStatus.PROCESSED, false, NOW).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation is past its expiry should report it as expired")
    void isExpired_whenProcessedOperationIsPastItsExpiry_shouldReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, true, NOW).isExpired(NOW.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation reaches its expiry exactly should not report it as expired")
    void isExpired_whenProcessedOperationReachesItsExpiryExactly_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, true, NOW).isExpired(NOW)).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation is still within its expiry should not report it as expired")
    void isExpired_whenProcessedOperationIsStillWithinItsExpiry_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, true, NOW).isExpired(NOW.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when the operation is still running should not report it as expired")
    void isExpired_whenOperationIsStillRunning_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.IN_PROCESS, true, NOW).isExpired(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a running operation carries no expiry at all should not report it as expired")
    void isExpired_whenRunningOperationCarriesNoExpiryAtAll_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.IN_PROCESS, true, null).isExpired(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("UT hasConflict() when a processed operation carries no response should still report no conflict")
    void hasConflict_whenProcessedOperationCarriesNoResponse_shouldStillReportNoConflict() {
        assertThat(operation(OperationStatus.PROCESSED, false, NOW).hasConflict()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation carries no response should still answer by its expiry")
    void isExpired_whenProcessedOperationCarriesNoResponse_shouldStillAnswerByItsExpiry() {
        // given
        Operation withoutResponse = operation(OperationStatus.PROCESSED, true, NOW);
        Operation withResponse = operation(OperationStatus.PROCESSED, true, NOW, RESPONSE);

        // when / then
        assertThat(withoutResponse.getResponse()).isNull();
        assertThat(withoutResponse.isExpired(NOW.plusSeconds(1)))
                .isEqualTo(withResponse.isExpired(NOW.plusSeconds(1)));
        assertThat(withoutResponse.isExpired(NOW.minusSeconds(1)))
                .isEqualTo(withResponse.isExpired(NOW.minusSeconds(1)));
    }

    @Test
    @DisplayName("UT setters should hand back everything they were given")
    void setters_shouldHandBackEverythingTheyWereGiven() {
        // given
        Operation tested = operation(OperationStatus.IN_PROCESS, true, NOW);
        UUID otherKey = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        // when
        tested.setIdempotencyKey(otherKey);
        tested.setStatus(OperationStatus.PROCESSED);
        tested.setFirstAttempt(false);
        tested.setResult("raw");
        tested.setResultType(OTHER_RESULT_TYPE);
        tested.setResponse(RESPONSE);
        tested.setFingerprint("other-fingerprint");
        tested.setExpiresAt(NOW.plusSeconds(60));
        tested.setCreatedAt(NOW.minusSeconds(60));

        // then
        assertThat(tested.getIdempotencyKey()).isEqualTo(otherKey);
        assertThat(tested.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(tested.isFirstAttempt()).isFalse();
        assertThat(tested.getResult()).isEqualTo("raw");
        assertThat(tested.getResultType()).isEqualTo(OTHER_RESULT_TYPE);
        assertThat(tested.getResponse()).isEqualTo(RESPONSE);
        assertThat(tested.getFingerprint()).isEqualTo("other-fingerprint");
        assertThat(tested.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(tested.getCreatedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("UT toString() should name the row without printing what it carries")
    void toString_shouldNameRowWithoutPrintingWhatItCarries() {
        // when
        String result = operation(OperationStatus.PROCESSED, true, NOW).toString();

        // then
        assertThat(result).contains(
                "idempotencyKey=" + KEY,
                "status=PROCESSED",
                "isFirstAttempt=true",
                "hasResult=true",
                "hasResponse=false",
                "fingerprint='fingerprint'"
        );
        assertThat(result).doesNotContain("raw");
    }

    private Operation operation(OperationStatus status, boolean firstAttempt, Instant expiresAt) {
        return operation(status, firstAttempt, expiresAt, null);
    }

    private Operation operation(OperationStatus status, boolean firstAttempt, Instant expiresAt, Response response) {
        return new Operation(KEY, status, firstAttempt, "raw", RESULT_TYPE, response, "fingerprint", expiresAt, NOW);
    }
}
