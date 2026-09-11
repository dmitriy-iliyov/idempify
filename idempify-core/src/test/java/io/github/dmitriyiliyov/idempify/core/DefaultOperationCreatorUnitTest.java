package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOperationCreatorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = TestClock.EPOCH;
    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);

    private final DefaultOperationCreator tested = new DefaultOperationCreator();

    @Test
    @DisplayName("UT create() should start the operation in process on its first attempt with no result yet")
    void create_shouldStartOperationInProcessOnFirstAttemptWithNoResultYet() {
        // when
        Operation result = tested.create(context("fingerprint"), NOW);

        // then
        assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(result.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
        assertThat(result.isFirstAttempt()).isTrue();
        assertThat(result.getResult()).isNull();
        assertThat(result.getFingerprint()).isEqualTo("fingerprint");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UT create() should carry the result type of the call into the claim")
    void create_shouldCarryResultTypeOfCallIntoClaim() {
        // when
        Operation result = tested.create(context("fingerprint"), NOW);

        // then
        assertThat(result.getResultType()).isEqualTo(RESULT_TYPE);
    }

    @Test
    @DisplayName("UT create() should leave the expiry unset because it is counted from completion")
    void create_shouldLeaveExpiryUnsetBecauseItIsCountedFromCompletion() {
        // when
        Operation result = tested.create(context("fingerprint"), NOW);

        // then
        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("UT create() should leave the response unset because the operation has not answered yet")
    void create_shouldLeaveResponseUnsetBecauseOperationHasNotAnsweredYet() {
        // when
        Operation result = tested.create(context("fingerprint"), NOW);

        // then
        assertThat(result.getResponse()).isNull();
    }

    @Test
    @DisplayName("UT create() when there is no fingerprint should leave it unset")
    void create_whenThereIsNoFingerprint_shouldLeaveItUnset() {
        // when
        Operation result = tested.create(context(null), NOW);

        // then
        assertThat(result.getFingerprint()).isNull();
    }

    private OperationContext context(String fingerprint) {
        return new DefaultOperationContext(RESULT_TYPE, () -> null, KEY, fingerprint);
    }
}
