package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class IdempotentProcessorUtilsUnitTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    @DisplayName("UT transactionStatusCheck() when the transaction is the caller's own should let it through")
    void transactionStatusCheck_whenValidateTransactionIsCallersOwn_shouldLetItThrough() {
        assertThatCode(() -> IdempotentProcessorUtils.validateTransactionStatus(new SimpleTransactionStatus(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT transactionStatusCheck() when joining a transaction already running should refuse it")
    void transactionStatusCheck_whenJoiningValidateTransactionAlreadyRunning_shouldRefuseIt() {
        assertThatThrownBy(() -> IdempotentProcessorUtils.validateTransactionStatus(new SimpleTransactionStatus(false)))
                .isInstanceOf(ForeignTransactionException.class)
                .hasMessageContaining("@Idempotent must own its transaction");
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when a nested call was refused should name the call site misplaced")
    void libraryFailureTypeOf_whenNestedCallWasRefused_shouldNameCallSiteMisplaced() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new NestedIdempotencyException()))
                .isEqualTo(FailureType.MISPLACED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the failure is an InterruptedException should name it interrupted")
    void libraryFailureTypeOf_whenFailureIsInterruptedException_shouldNameItInterrupted() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new InterruptedException()))
                .isEqualTo(FailureType.INTERRUPTED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when any failure is thrown on an interrupted thread should name it interrupted")
    void libraryFailureTypeOf_whenAnyFailureIsThrownOnInterruptedThread_shouldNameItInterrupted() {
        // given
        Thread.currentThread().interrupt();

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new IllegalStateException("aborted")))
                .isEqualTo(FailureType.INTERRUPTED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when a nested call is refused on an interrupted thread should name one type only")
    void libraryFailureTypeOf_whenNestedCallIsRefusedOnInterruptedThread_shouldNameOneTypeOnly() {
        // given
        Thread.currentThread().interrupt();

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new NestedIdempotencyException()))
                .isEqualTo(FailureType.MISPLACED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the outcome was not recorded on an interrupted thread should name it so")
    void libraryFailureTypeOf_whenOutcomeWasNotRecordedOnInterruptedThread_shouldNameItSo() {
        // given
        Thread.currentThread().interrupt();
        ResultNotRecordedException failure = new ResultNotRecordedException(KEY, new IllegalStateException("driver"));

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(failure)).isEqualTo(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the call failed before the operation ran on an interrupted thread should name it so")
    void libraryFailureTypeOf_whenCallFailedBeforeOperationRanOnInterruptedThread_shouldNameItSo() {
        // given
        Thread.currentThread().interrupt();
        EarlyFailureException failure = new EarlyFailureException(KEY, new IllegalStateException("driver"));

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(failure)).isEqualTo(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the failure is the operation's own should return null")
    void libraryFailureTypeOf_whenFailureIsOperationsOwn_shouldReturnNull() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new IllegalStateException("card declined"))).isNull();
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the call failed before the operation ran should name it so")
    void libraryFailureTypeOf_whenCallFailedBeforeOperationRan_shouldNameItSo() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new EarlyFailureException(KEY, new IllegalStateException())))
                .isEqualTo(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the claim could not be taken should name it a failure before the operation ran")
    void libraryFailureTypeOf_whenClaimCouldNotBeTaken_shouldNameItFailureBeforeRun() {
        // given
        OperationLockFailedException failure = new OperationLockFailedException(KEY, new IllegalStateException());

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(failure))
                .isEqualTo(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the outcome was not recorded should name it so")
    void libraryFailureTypeOf_whenOutcomeWasNotRecorded_shouldNameItSo() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new ResultNotRecordedException(KEY, new IllegalStateException())))
                .isEqualTo(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when a failed operation could be neither released nor kept should name its outcome not recorded")
    void libraryFailureTypeOf_whenFailedOperationCouldBeNeitherReleasedNorKept_shouldNameItsOutcomeNotRecorded() {
        // given
        FailureNotRecordedException failure = new FailureNotRecordedException(
                KEY, new IllegalStateException("store down"), new IllegalArgumentException("card declined"));

        // when / then
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(failure)).isEqualTo(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("UT libraryFailureTypeOf() when the call ran inside a transaction it does not own should name it misplaced")
    void libraryFailureTypeOf_whenCallRanInsideTransactionItDoesNotOwn_shouldNameItMisplaced() {
        assertThat(IdempotentProcessorUtils.libraryFailureTypeOf(new ForeignTransactionException("joined")))
                .isEqualTo(FailureType.MISPLACED);
    }

    @Test
    @DisplayName("UT CallProgress should not count the operation started until it is marked")
    void callProgress_shouldNotCountOperationStartedUntilItIsMarked() {
        // given
        IdempotentProcessorUtils.CallProgress tested = new IdempotentProcessorUtils.CallProgress();

        // when / then
        assertThat(tested.isOperationStarted()).isFalse();
        tested.markOperationStarted();
        assertThat(tested.isOperationStarted()).isTrue();
        assertThat(tested.isKeyReleased()).isFalse();
    }

    @Test
    @DisplayName("UT CallProgress should not count the key released until it is marked")
    void callProgress_shouldNotCountKeyReleasedUntilItIsMarked() {
        // given
        IdempotentProcessorUtils.CallProgress tested = new IdempotentProcessorUtils.CallProgress();

        // when / then
        assertThat(tested.isKeyReleased()).isFalse();
        tested.markKeyReleased();
        assertThat(tested.isKeyReleased()).isTrue();
        assertThat(tested.isOperationStarted()).isFalse();
    }

    @Test
    @DisplayName("UT class should be a utility holder that cannot be instantiated or extended")
    void class_shouldBeUtilityHolderThatCannotBeInstantiatedOrExtended() throws Exception {
        // given
        Constructor<IdempotentProcessorUtils> constructor = IdempotentProcessorUtils.class.getDeclaredConstructor();

        // when / then
        assertThat(Modifier.isFinal(IdempotentProcessorUtils.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
