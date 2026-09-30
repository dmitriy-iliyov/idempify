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
    private static final Response RESPONSE = new DefaultResponse(201, new byte [0], "application/json", Map.of());

    @Test
    @DisplayName("UT ofContext() should start the operation in process on its first attempt with no result yet")
    void ofContext_shouldStartOperationInProcessOnFirstAttemptWithNoResultYet() {
        // when
        Operation result = Operation.ofContext(context("fingerprint"));

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isNull();
        assertThat(result.getResultType()).isEqualTo(RESULT_TYPE);
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    @DisplayName("UT ofContext() should leave creation time, expiry and response unset for the store and the completion to fill")
    void ofContext_shouldLeaveCreationTimeExpiryAndResponseUnsetForStoreAndCompletionToFill() {
        // when
        Operation result = Operation.ofContext(context("fingerprint"));

        // then
        assertThat(result.getCreatedAt()).isNull();
        assertThat(result.getRemainingMillis()).isNull();
        assertThat(result.getResponse()).isNull();
    }

    @Test
    @DisplayName("UT ofContext() when there is no fingerprint should leave it unset")
    void ofContext_whenThereIsNoFingerprint_shouldLeaveItUnset() {
        // when
        Operation result = Operation.ofContext(context(null));

        // then
        assertThat(result.getFingerprint()).isNull();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation has time left should not report it as expired")
    void isExpired_whenProcessedOperationHasTimeLeft_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, 1L).isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation has exactly no time left should report it as expired")
    void isExpired_whenProcessedOperationHasExactlyNoTimeLeft_shouldReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, 0L).isExpired()).isTrue();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation is past its expiry should report it as expired")
    void isExpired_whenProcessedOperationIsPastItsExpiry_shouldReportItAsExpired() {
        assertThat(operation(OperationStatus.PROCESSED, -1L).isExpired()).isTrue();
    }

    @Test
    @DisplayName("UT isExpired() when a running operation carries no expiry at all should not report it as expired")
    void isExpired_whenRunningOperationCarriesNoExpiryAtAll_shouldNotReportItAsExpired() {
        assertThat(operation(OperationStatus.IN_PROCESS, null).isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a running operation carries a spent expiry should still not report it as expired")
    void isExpired_whenRunningOperationCarriesSpentExpiry_shouldStillNotReportItAsExpired() {
        assertThat(operation(OperationStatus.IN_PROCESS, -1L).isExpired()).isFalse();
    }

    @Test
    @DisplayName("UT isExpired() when a processed operation carries no response should still answer by its expiry")
    void isExpired_whenProcessedOperationCarriesNoResponse_shouldStillAnswerByItsExpiry() {
        // given
        Operation withoutResponse = operation(OperationStatus.PROCESSED, -1L, null);
        Operation withResponse = operation(OperationStatus.PROCESSED, -1L, RESPONSE);

        // when / then
        assertThat(withoutResponse.getResponse()).isNull();
        assertThat(withoutResponse.isExpired()).isEqualTo(withResponse.isExpired());
    }

    @Test
    @DisplayName("UT toString() should name the row without printing what it carries")
    void toString_shouldNameRowWithoutPrintingWhatItCarries() {
        // when
        String result = operation(OperationStatus.PROCESSED, 60_000L).toString();

        // then
        assertThat(result).contains(
                "idempotencyKey=" + KEY,
                "status=PROCESSED",
                "isFirstAttempt=true",
                "hasResult=true",
                "hasResponse=false",
                "fingerprint='fingerprint'",
                "remainingMillis=60000"
        );
        assertThat(result).doesNotContain("raw");
    }

    @Test
    @DisplayName("UT toString() when the row carries a response but no result should say so")
    void toString_whenRowCarriesResponseButNoResult_shouldSaySo() {
        // given
        Operation tested = new Operation(
                KEY, OperationStatus.PROCESSED, true, null, RESULT_TYPE, RESPONSE, "fingerprint", 60_000L, NOW);

        // when
        String result = tested.toString();

        // then
        assertThat(result).contains("hasResult=false", "hasResponse=true");
    }

    private Operation operation(OperationStatus status, Long remainingMillis) {
        return operation(status, remainingMillis, null);
    }

    private Operation operation(OperationStatus status, Long remainingMillis, Response response) {
        return new Operation(KEY, status, true, "raw", RESULT_TYPE, response, "fingerprint", remainingMillis, NOW);
    }

    private OperationContext context(String fingerprint) {
        return new DefaultOperationContext(RESULT_TYPE, () -> null, KEY, fingerprint);
    }
}
