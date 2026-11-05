package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.conflict.OperationInProgressException;
import io.github.dmitriyiliyov.idempify.core.fingerprint.FingerprintMismatchException;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The one place a failure of the call is counted: what the processors below throw is turned into exactly one
 * {@code onFailure}, of the type the exception names, and the caller receives that very exception.
 */
@ExtendWith(MockitoExtension.class)
class FailureObservingIdempotentProcessorDecoratorUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    IdempotentProcessor delegate;

    @Mock
    IdempotencyEventListener eventListener;

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("UT constructor when delegate is null should throw NullPointerException")
    void constructor_whenDelegateIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new FailureObservingIdempotentProcessorDecorator(null, eventListener))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("delegate cannot be null");
    }

    @Test
    @DisplayName("UT constructor when eventListener is null should throw NullPointerException")
    void constructor_whenEventListenerIsNull_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new FailureObservingIdempotentProcessorDecorator(delegate, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("eventListener cannot be null");
    }

    @Test
    @DisplayName("UT process() when the call is answered should hand back the answer and count no failure")
    void process_whenCallIsAnswered_shouldHandBackAnswerAndCountNoFailure() throws Throwable {
        // given
        OperationContext context = context();
        OperationMetadata metadata = metadata();
        when(delegate.process(context, metadata)).thenReturn("charged");

        // when
        Object result = tested().process(context, metadata);

        // then
        assertThat(result).isEqualTo("charged");
        verifyNoInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the call failed before the operation ran should count it so and throw that very failure")
    void process_whenCallFailedBeforeOperationRan_shouldCountItSoAndThrowThatVeryFailure() throws Throwable {
        // given
        EarlyFailureException failure = new EarlyFailureException(KEY, new IllegalStateException("store down"));
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.FAILED_BEFORE_RUN);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the claim could not be taken should count it as a failure before the operation ran")
    void process_whenClaimCouldNotBeTaken_shouldCountItAsFailureBeforeRun() throws Throwable {
        // given
        OperationLockFailedException failure =
                new OperationLockFailedException(KEY, new IllegalStateException("store down"));
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.FAILED_BEFORE_RUN);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the outcome of the operation was not recorded should count it so and throw that very failure")
    void process_whenOutcomeOfOperationWasNotRecorded_shouldCountItSoAndThrowThatVeryFailure() throws Throwable {
        // given
        ResultNotRecordedException failure = new ResultNotRecordedException(KEY, new IllegalStateException("store down"));
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.OUTCOME_NOT_RECORDED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the failed operation could be neither released nor kept should count its outcome as not recorded")
    void process_whenFailedOperationCouldBeNeitherReleasedNorKept_shouldCountItsOutcomeAsNotRecorded() throws Throwable {
        // given
        FailureNotRecordedException failure = new FailureNotRecordedException(
                KEY, new IllegalStateException("store down"), new IllegalArgumentException("card declined"));
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.OUTCOME_NOT_RECORDED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the call was made inside a transaction it does not own should count it as misplaced")
    void process_whenCallWasMadeInsideTransactionItDoesNotOwn_shouldCountItAsMisplaced() throws Throwable {
        // given
        ForeignTransactionException failure = new ForeignTransactionException("must own its transaction");
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.MISPLACED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when a nested call was refused should count it as misplaced")
    void process_whenNestedCallWasRefused_shouldCountItAsMisplaced() throws Throwable {
        // given
        NestedIdempotencyException failure = new NestedIdempotencyException();
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.MISPLACED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the operation was interrupted should count it as interrupted")
    void process_whenOperationWasInterrupted_shouldCountItAsInterrupted() throws Throwable {
        // given
        InterruptedException failure = new InterruptedException("shutting down");
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verify(eventListener).onFailure(FailureType.INTERRUPTED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the operation failed on an interrupted thread should count it as interrupted and leave the flag set")
    void process_whenOperationFailedOnInterruptedThread_shouldCountItAsInterruptedAndLeaveFlagSet() throws Throwable {
        // given
        IllegalStateException failure = new IllegalStateException("gateway call aborted");
        when(delegate.process(any(), any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw failure;
        });

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(eventListener).onFailure(FailureType.INTERRUPTED);
        verifyNoMoreInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the operation itself failed should count no failure of the call")
    void process_whenOperationItselfFailed_shouldCountNoFailureOfCall() throws Throwable {
        // given
        IllegalStateException failure = new IllegalStateException("card declined");
        givenDelegateThrows(failure);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
        verifyNoInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the call was refused for its fingerprint should count no failure of the call")
    void process_whenCallWasRefusedForItsFingerprint_shouldCountNoFailureOfCall() throws Throwable {
        // given
        FingerprintMismatchException refusal = new FingerprintMismatchException(null, "mismatch");
        givenDelegateThrows(refusal);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(refusal);
        verifyNoInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the call was refused for a conflict should count no failure of the call")
    void process_whenCallWasRefusedForConflict_shouldCountNoFailureOfCall() throws Throwable {
        // given
        OperationInProgressException refusal = new OperationInProgressException("already in progress");
        givenDelegateThrows(refusal);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(refusal);
        verifyNoInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the delegate throws an error should let that very error out and count nothing")
    void process_whenDelegateThrowsError_shouldLetThatVeryErrorOutAndCountNothing() throws Throwable {
        // given
        OutOfMemoryError error = new OutOfMemoryError("heap exhausted");
        givenDelegateThrows(error);

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(error);
        verifyNoInteractions(eventListener);
    }

    @Test
    @DisplayName("UT process() when the event listener fails should still throw the call's own failure")
    void process_whenEventListenerFails_shouldStillThrowCallsOwnFailure() throws Throwable {
        // given
        EarlyFailureException failure = new EarlyFailureException(KEY, new IllegalStateException("store down"));
        givenDelegateThrows(failure);
        doThrow(new IllegalStateException("metrics registry down")).when(eventListener).onFailure(any());

        // when / then
        assertThatThrownBy(() -> tested().process(context(), metadata())).isSameAs(failure);
    }

    private FailureObservingIdempotentProcessorDecorator tested() {
        return new FailureObservingIdempotentProcessorDecorator(delegate, eventListener);
    }

    private void givenDelegateThrows(Throwable thrown) throws Throwable {
        when(delegate.process(any(), any())).thenThrow(thrown);
    }

    private static OperationContext context() {
        return new DefaultOperationContext(ResultType.ofClass(String.class), () -> "charged", KEY, null);
    }

    private static OperationMetadata metadata() {
        return TestOperationMetadata.builder().build();
    }
}
