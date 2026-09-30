package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitConflictHandlerUnitTest {

    private static final ResultType RESULT_TYPE = ResultType.ofClass(String.class);
    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    NonBlockingOperationRepository repository;

    @Test
    @DisplayName("UT constructor when config is null should throw NullPointerException")
    void constructor_whenConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                null, repository, deserializer(), TestClock.standingStill()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config cannot be null");
    }

    @Test
    @DisplayName("UT constructor when the config leaves a setting undecided should throw IllegalArgumentException")
    void constructor_whenConfigLeavesSettingUndecided_shouldThrowIllegalArgumentException() {
        // given
        WaitConflictHandlerConfig config = WaitConflictHandlerConfig.builder()
                .delay(50)
                .multiplier(2.0)
                .maxAttempts(3)
                .build();

        // when / then
        assertThatThrownBy(() -> new WaitConflictHandler(
                config, repository, deserializer(), TestClock.standingStill()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config must decide every setting before it reaches a handler");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), null, deserializer(), TestClock.standingStill()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when deserialize is null should throw NullPointerException")
    void constructor_whenDeserializeIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), repository, null, TestClock.standingStill()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("deserialize cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), repository, deserializer(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT handle() when the operation is already processed should return it deserialized")
    void handle_whenOperationIsAlreadyProcessed_shouldReturnItDeserialized() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(Optional.of(operation(OperationStatus.PROCESSED)));

        // when
        NonBlockingOperation result = tested.handle(context());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isEqualTo("raw");
        assertThat(result.getResultType()).isEqualTo(RESULT_TYPE);
        verify(repository, times(1)).find(KEY);
    }

    @Test
    @DisplayName("UT handle() when the processed operation returned null should hand back an operation without a result")
    void handle_whenProcessedOperationReturnedNull_shouldHandBackOperationWithoutResult() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(Optional.of(operation(OperationStatus.PROCESSED, null)));

        // when
        NonBlockingOperation result = tested.handle(context());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(result.getResult()).isNull();
    }

    @Test
    @DisplayName("UT handle() when the operation completes on a later attempt should return it deserialized")
    void handle_whenOperationCompletesOnLaterAttempt_shouldReturnItDeserialized() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(
                Optional.of(operation(OperationStatus.IN_PROCESS)),
                Optional.of(operation(OperationStatus.PROCESSED))
        );

        // when
        NonBlockingOperation result = tested.handle(context());

        // then
        assertThat(result.getResult()).isEqualTo("raw");
        verify(repository, times(2)).find(KEY);
    }

    @Test
    @DisplayName("UT handle() when the operation kept a failure should hand it back without waiting any further")
    void handle_whenOperationKeptFailure_shouldHandItBackWithoutWaitingAnyFurther() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(Optional.of(operation(OperationStatus.EXCEPTIONALLY_PROCESSED)));

        // when
        NonBlockingOperation result = tested.handle(context());

        // then
        assertThat(result.getStatus()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
        verify(repository, times(1)).find(KEY);
    }

    @Test
    @DisplayName("UT handle() when the operation row is gone should throw OperationDisappearedException")
    void handle_whenOperationRowIsGone_shouldThrowOperationDisappearedException() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.handle(context()))
                .isInstanceOf(OperationDisappearedException.class)
                .hasMessageContaining(KEY.toString());

    }

    @Test
    @DisplayName("UT handle() when the operation never completes should throw WaitAttemptsExhaustedException after every attempt")
    void handle_whenOperationNeverCompletes_shouldThrowWaitAttemptsExhaustedExceptionAfterEveryAttempt() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.find(KEY)).thenReturn(Optional.of(operation(OperationStatus.IN_PROCESS)));

        // when / then
        assertThatThrownBy(() -> tested.handle(context()))
                .isInstanceOf(WaitAttemptsExhaustedException.class)
                .hasMessageContaining(KEY.toString());

        verify(repository, times(3)).find(KEY);
    }

    @Test
    @DisplayName("UT handle() when the max duration is already spent should throw WaitTimeoutException without reading the operation")
    void handle_whenMaxDurationIsAlreadySpent_shouldThrowWaitTimeoutExceptionWithoutReadingOperation() {
        // given
        WaitConflictHandler tested = handler(TestClock.steppingBy(Duration.ofSeconds(1)));

        // when / then
        assertThatThrownBy(() -> tested.handle(context()))
                .isInstanceOf(WaitTimeoutException.class)
                .hasMessageContaining(KEY.toString());

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("UT handle() when the waiting thread is interrupted should abort the wait and leave the thread interrupted")
    void handle_whenWaitingThreadIsInterrupted_shouldAbortWaitAndLeaveThreadInterrupted() {
        // given
        WaitConflictHandler tested = new WaitConflictHandler(
                WaitConflictHandlerConfig.builder(WaitConflictHandlerConfig.defaults()).delay(50).maxAttempts(3).build(),
                repository,
                deserializer(),
                TestClock.standingStill()
        );

        when(repository.find(KEY)).thenReturn(Optional.of(operation(OperationStatus.IN_PROCESS)));

        // when / then
        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> tested.handle(context()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Interrupted while waiting")
                    .hasCauseInstanceOf(InterruptedException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private WaitConflictHandler handler(Clock clock) {
        WaitConflictHandlerConfig config = WaitConflictHandlerConfig.builder()
                .delay(1)
                .multiplier(1)
                .maxAttempts(3)
                .maxDuration(Duration.ofMillis(500))
                .build();
        return new WaitConflictHandler(config, repository, deserializer(), clock);
    }

    private static ConflictContext context() {
        return new DefaultConflictContext(KEY, RESULT_TYPE);
    }

    /**
     * Decodes the row as it is, the result staying the raw text: what the handler owes is choosing which row to
     * decode and handing the call site's type along, not the decoding itself.
     */
    private static OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserializer() {
        return (row, type) -> new NonBlockingOperation(
                row.idempotencyKey(),
                row.status(),
                row.isFirstAttempt(),
                row.result(),
                type,
                null,
                row.fingerprint(),
                row.remainingMillis(),
                row.createdAt(),
                row.lockToken(),
                row.remainingLockMillis()
        );
    }

    private static RawNonBlockingOperation operation(OperationStatus status) {
        return operation(status, "raw");
    }

    private static RawNonBlockingOperation operation(OperationStatus status, String result) {
        return new RawNonBlockingOperation(
                KEY,
                status,
                false,
                result,
                null,
                "fingerprint",
                OperationStatus.PROCESSED.equals(status) ? 60_000L : null,
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null
        );
    }
}
