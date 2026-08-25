package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
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

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    OperationRepository repository;

    @Mock
    ResultDeserializer resultDeserializer;

    @Test
    @DisplayName("UT constructor when config is null should throw NullPointerException")
    void constructor_whenConfigIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                null, repository, resultDeserializer, TestClock.standingStill()))
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
                config, repository, resultDeserializer, TestClock.standingStill()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config must decide every setting before it reaches a handler");
    }

    @Test
    @DisplayName("UT constructor when repository is null should throw NullPointerException")
    void constructor_whenRepositoryIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), null, resultDeserializer, TestClock.standingStill()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("repository cannot be null");
    }

    @Test
    @DisplayName("UT constructor when resultDeserializer is null should throw NullPointerException")
    void constructor_whenResultDeserializerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), repository, null, TestClock.standingStill()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("responseDeserializer cannot be null");
    }

    @Test
    @DisplayName("UT constructor when clock is null should throw NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new WaitConflictHandler(
                WaitConflictHandlerConfig.defaults(), repository, resultDeserializer, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clock cannot be null");
    }

    @Test
    @DisplayName("UT requiresTransaction() should return false")
    void requiresTransaction_shouldReturnFalse() {
        assertThat(handler(TestClock.standingStill()).requiresTransaction()).isFalse();
    }

    @Test
    @DisplayName("UT handle() when the operation is already processed should return its deserialized result")
    void handle_whenOperationIsAlreadyProcessed_shouldReturnItsDeserializedResult() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(operation(OperationStatus.PROCESSED)));
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        String result = tested.handle(new DefaultConflictContext<>(KEY, String.class));

        // then
        assertThat(result).isEqualTo("deserialized");
        verify(repository, times(1)).findByIdempotencyKey(KEY);
    }

    @Test
    @DisplayName("UT handle() when the operation completes on a later attempt should return its deserialized result")
    void handle_whenOperationCompletesOnLaterAttempt_shouldReturnItsDeserializedResult() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.findByIdempotencyKey(KEY)).thenReturn(
                Optional.of(operation(OperationStatus.IN_PROCESS)),
                Optional.of(operation(OperationStatus.PROCESSED))
        );
        when(resultDeserializer.deserialize("raw", String.class)).thenReturn("deserialized");

        // when
        String result = tested.handle(new DefaultConflictContext<>(KEY, String.class));

        // then
        assertThat(result).isEqualTo("deserialized");
        verify(repository, times(2)).findByIdempotencyKey(KEY);
    }

    @Test
    @DisplayName("UT handle() when the operation row is gone should throw OperationDisappearedException")
    void handle_whenOperationRowIsGone_shouldThrowOperationDisappearedException() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.handle(new DefaultConflictContext<>(KEY, String.class)))
                .isInstanceOf(OperationDisappearedException.class)
                .hasMessageContaining(KEY.toString());

        verifyNoInteractions(resultDeserializer);
    }

    @Test
    @DisplayName("UT handle() when the operation never completes should throw WaitAttemptsExhaustedException after every attempt")
    void handle_whenOperationNeverCompletes_shouldThrowWaitAttemptsExhaustedExceptionAfterEveryAttempt() {
        // given
        WaitConflictHandler tested = handler(TestClock.standingStill());

        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(operation(OperationStatus.IN_PROCESS)));

        // when / then
        assertThatThrownBy(() -> tested.handle(new DefaultConflictContext<>(KEY, String.class)))
                .isInstanceOf(WaitAttemptsExhaustedException.class)
                .hasMessageContaining(KEY.toString());

        verify(repository, times(3)).findByIdempotencyKey(KEY);
        verifyNoInteractions(resultDeserializer);
    }

    @Test
    @DisplayName("UT handle() when the max duration is already spent should throw WaitTimeoutException without reading the operation")
    void handle_whenMaxDurationIsAlreadySpent_shouldThrowWaitTimeoutExceptionWithoutReadingOperation() {
        // given
        WaitConflictHandler tested = handler(TestClock.steppingBy(Duration.ofSeconds(1)));

        // when / then
        assertThatThrownBy(() -> tested.handle(new DefaultConflictContext<>(KEY, String.class)))
                .isInstanceOf(WaitTimeoutException.class)
                .hasMessageContaining(KEY.toString());

        verifyNoInteractions(repository, resultDeserializer);
    }

    @Test
    @DisplayName("UT handle() when the waiting thread is interrupted should abort the wait and leave the thread interrupted")
    void handle_whenWaitingThreadIsInterrupted_shouldAbortWaitAndLeaveThreadInterrupted() {
        // given
        WaitConflictHandler tested = new WaitConflictHandler(
                WaitConflictHandlerConfig.builder(WaitConflictHandlerConfig.defaults()).delay(50).maxAttempts(3).build(),
                repository,
                resultDeserializer,
                TestClock.standingStill()
        );

        when(repository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(operation(OperationStatus.IN_PROCESS)));

        // when / then
        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> tested.handle(new DefaultConflictContext<>(KEY, String.class)))
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
        return new WaitConflictHandler(config, repository, resultDeserializer, clock);
    }

    private Operation operation(OperationStatus status) {
        return new Operation(
                KEY,
                status,
                false,
                "raw",
                "fingerprint",
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
