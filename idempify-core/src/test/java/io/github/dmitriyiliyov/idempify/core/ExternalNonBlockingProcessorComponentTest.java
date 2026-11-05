package io.github.dmitriyiliyov.idempify.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What only {@link ExternalNonBlockingIdempotentProcessor} promises: the business method runs outside any
 * transaction of the library, so its effect stays whatever happens to the record of the operation.
 */
class ExternalNonBlockingProcessorComponentTest extends AbstractNonBlockingProcessorComponentTest {

    @Override
    protected TypeAwareIdempotentProcessor processor(NonBlockingOperationManager manager) {
        return new ExternalNonBlockingIdempotentProcessor(manager, channel, eventListener);
    }

    @Override
    protected ProcessorType processorType() {
        return ProcessorType.EXTERNAL_NON_BLOCKING;
    }

    @Test
    @DisplayName("CT request when the operation runs should run it outside any transaction")
    void request_whenOperationRuns_shouldRunItOutsideAnyTransaction() throws Throwable {
        // given
        List<Boolean> seenByOperation = new ArrayList<>();
        ChargingOperation operation = ChargingOperation.running(() -> {
            seenByOperation.add(TransactionSynchronizationManager.isActualTransactionActive());
            return "charged";
        }, ledger);

        // when
        call(KEY, metadata().build(), operation);

        // then
        assertThat(seenByOperation).containsExactly(false);
        assertThat(transactionManager.commits()).isZero();
        assertThat(transactionManager.rollbacks()).isZero();
    }

    @Test
    @DisplayName("CT request when the failure is kept on the record should leave the operation's effect in place")
    void request_whenFailureIsKeptOnRecord_shouldLeaveOperationsEffectInPlace() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(ledger.charges()).containsExactly("charge #1");
        assertThat(repository.row(KEY).orElseThrow().status()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
    }

    @Test
    @DisplayName("CT request when keeping the failure on the record fails should say so, record no response and keep the claim")
    void request_whenKeepingFailureOnRecordFails_shouldSaySoRecordNoResponseAndKeepClaim() {
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
            assertThat(row.lockToken()).isNotNull();
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.exceptions).isZero();
        assertThat(eventListener.exceptionallySuccesses).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("CT request when releasing the key fails should say so, record no response and keep the claim")
    void request_whenReleasingKeyFails_shouldSaySoRecordNoResponseAndKeepClaim() {
        // given
        IllegalStateException storeDown = new IllegalStateException("store down");
        repository.failNext(InMemoryNonBlockingOperationRepository.Write.DELETE, storeDown);
        ChargingOperation operation = ChargingOperation.failing(new IllegalStateException("gateway unavailable"), ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, recordingFor(IllegalArgumentException.class), operation))
                .isInstanceOf(FailureNotRecordedException.class)
                .hasCause(storeDown);

        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNotNull();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.exceptions).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }
}
