package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What only {@link TransactionalNonBlockingIdempotentProcessor} promises: the business effect and the record
 * of the operation's outcome commit or roll back together, while the claim lives apart from both.
 */
class TransactionalNonBlockingProcessorComponentTest extends AbstractNonBlockingProcessorComponentTest {

    @Override
    protected TypeAwareIdempotentProcessor processor(NonBlockingOperationManager manager) {
        return new TransactionalNonBlockingIdempotentProcessor(
                manager,
                channel,
                eventListener,
                new TransactionTemplate(transactionManager)
        );
    }

    @Override
    protected ProcessorType processorType() {
        return ProcessorType.TRANSACTIONAL_NON_BLOCKING;
    }

    @Test
    @DisplayName("CT request when the operation completes should commit its effect together with the result")
    void request_whenOperationCompletes_shouldCommitItsEffectTogetherWithResult() throws Throwable {
        // when
        call(KEY, metadata().build(), ChargingOperation.returning("charged", ledger));

        // then
        assertThat(ledger.charges()).containsExactly("charge #1");
        assertThat(repository.row(KEY).orElseThrow().status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(transactionManager.commits()).isEqualTo(1);
        assertThat(transactionManager.rollbacks()).isZero();
    }

    @Test
    @DisplayName("CT request when the operation runs should run it inside the processor's own transaction")
    void request_whenOperationRuns_shouldRunItInsideProcessorsOwnTransaction() throws Throwable {
        // given
        List<Boolean> seenByOperation = new java.util.ArrayList<>();
        ChargingOperation operation = ChargingOperation.running(() -> {
            seenByOperation.add(TransactionSynchronizationManager.isActualTransactionActive());
            return "charged";
        }, ledger);

        // when
        call(KEY, metadata().build(), operation);

        // then
        assertThat(seenByOperation).containsExactly(true);
    }

    @Test
    @DisplayName("CT request when the operation completes should tell the transport only after the transaction committed")
    void request_whenOperationCompletes_shouldTellTransportOnlyAfterTransactionCommitted() throws Throwable {
        // when
        call(KEY, metadata().build(), ChargingOperation.returning("charged", ledger));

        // then
        assertThat(channel.commitsAtPublish).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the failure is kept on the record should roll the operation's effect back")
    void request_whenFailureIsKeptOnRecord_shouldRollOperationsEffectBack() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(ledger.charges()).isEmpty();
        assertThat(repository.row(KEY).orElseThrow().status()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
        assertThat(transactionManager.commits()).isZero();
    }

    @Test
    @DisplayName("CT request when a checked failure is kept should roll the operation's effect back")
    void request_whenCheckedFailureIsKept_shouldRollOperationsEffectBack() {
        // given
        java.io.IOException thrown = new java.io.IOException("gateway timeout");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(ledger.charges()).isEmpty();
    }

    @Test
    @DisplayName("CT request when the failure releases the key should roll the operation's effect back")
    void request_whenFailureReleasesKey_shouldRollOperationsEffectBack() {
        // given
        IllegalStateException thrown = new IllegalStateException("gateway unavailable");
        OperationMetadata metadata = metadata()
                .responseCacheConfig(io.github.dmitriyiliyov.idempify.core.config.ResponseConfig.builder()
                        .recordFor(List.of())
                        .build())
                .build();

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata, ChargingOperation.failing(thrown, ledger))).isSameAs(thrown);

        assertThat(ledger.charges()).isEmpty();
        assertThat(repository.row(KEY)).isEmpty();
    }

    @Test
    @DisplayName("CT request when the commit fails should keep neither the effect nor the result and let the next attempt run")
    void request_whenCommitFails_shouldKeepNeitherEffectNorResultAndLetNextAttemptRun() throws Throwable {
        // given
        transactionManager.failNextCommit();
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isExactlyInstanceOf(ResultNotRecordedException.class)
                .hasCauseInstanceOf(TransactionSystemException.class);

        assertThat(ledger.charges()).isEmpty();
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(row.lockToken()).isNull();
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.successes).isZero();

        assertThat(call(KEY, metadata().build(), operation)).isEqualTo("charged");
        assertThat(ledger.charges()).containsExactly("charge #2");
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("CT request when the transaction cannot begin should run nothing and let the claim go")
    void request_whenTransactionCannotBegin_shouldRunNothingAndLetClaimGo() {
        // given
        transactionManager.failNextBegin();
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(EarlyFailureException.class)
                .hasCauseInstanceOf(org.springframework.transaction.CannotCreateTransactionException.class);

        assertThat(operation.calls).isZero();
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(row.lockToken()).isNull();
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.failures).containsExactly(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("CT request when the claim was taken over while the operation ran should roll the operation's effect back")
    void request_whenClaimWasTakenOverWhileOperationRan_shouldRollOperationsEffectBack() {
        // given
        ChargingOperation operation = ChargingOperation.running(() -> {
            clock.advance(LOCK_TTL);
            repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
            return "charged";
        }, ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isExactlyInstanceOf(ResultNotRecordedException.class)
                .hasCauseInstanceOf(OperationLockLostException.class);

        assertThat(ledger.charges()).isEmpty();
    }

    @Test
    @DisplayName("CT request when keeping the failure on the record fails should say so, record no response and let the claim go")
    void request_whenKeepingFailureOnRecordFails_shouldSaySoRecordNoResponseAndLetClaimGo() {
        // given
        IllegalStateException storeDown = new IllegalStateException("store down");
        repository.failNext(InMemoryNonBlockingOperationRepository.Write.KEEP_FAILURE, storeDown);
        ChargingOperation operation = ChargingOperation.failing(new IllegalArgumentException("card declined"), ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasCause(storeDown);

        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(row.lockToken()).isNull();
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.exceptions).isZero();
        assertThat(eventListener.exceptionallySuccesses).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("CT request when releasing the key fails should say so, record no response and let the claim go")
    void request_whenReleasingKeyFails_shouldSaySoRecordNoResponseAndLetClaimGo() {
        // given
        IllegalStateException storeDown = new IllegalStateException("store down");
        repository.failNext(InMemoryNonBlockingOperationRepository.Write.DELETE, storeDown);
        ChargingOperation operation = ChargingOperation.failing(new IllegalStateException("gateway unavailable"), ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, recordingFor(IllegalArgumentException.class), operation))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasCause(storeDown);

        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNull();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.exceptions).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }
}
