package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.ResponseConfig;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.conflict.*;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs both processors of the non-blocking branch through the whole core chain - processor, operation manager,
 * (de)serializers, fingerprint policy and matcher, conflict handlers - over stand-ins for the modules core does
 * not own: the store, the result (de)serializer, the state channel and the transaction manager. The scenarios
 * here are the ones both processors answer alike; what tells them apart lives in the subclasses.
 */
abstract class AbstractNonBlockingProcessorComponentTest {

    protected static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    protected static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    protected static final UUID FOREIGN_LOCK_TOKEN = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    protected static final Duration TTL = Duration.parse(IdempifyDefaults.TTL_VALUE);
    protected static final Duration LOCK_TTL = Duration.ofMinutes(5);

    protected final TestClock clock = TestClock.standingStill();
    protected final InMemoryNonBlockingOperationRepository repository = new InMemoryNonBlockingOperationRepository(clock);
    protected final InMemoryLedger ledger = new InMemoryLedger();
    protected final RecordingTransactionManager transactionManager = new RecordingTransactionManager(repository, ledger);
    protected final RecordingStateChannel channel = new RecordingStateChannel(transactionManager::commits);
    protected final RecordingEventListener eventListener = new RecordingEventListener();
    private final FingerprintPolicy fingerprintPolicy = new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());
    private final PassThroughResultSerializer resultSerializer = new PassThroughResultSerializer();
    private final NonBlockingOperationDeserializer deserializer =
            new NonBlockingOperationDeserializer(resultSerializer, new NullResponseSerializer());

    private IdempotentProcessor processor;

    protected abstract TypeAwareIdempotentProcessor processor(NonBlockingOperationManager manager);

    protected abstract ProcessorType processorType();

    @BeforeEach
    void setUp() {
        NonBlockingOperationManager manager = new DefaultNonBlockingOperationManager(
                repository,
                new SequentialLockTokenGenerator(),
                new NonBlockingOperationSerializer(resultSerializer, new NullResponseSerializer()),
                deserializer,
                new DefaultFingerprintMatcher(eventListener),
                resultSerializer,
                eventListener
        );
        processor = new FailureObservingIdempotentProcessorDecorator(
                new DelegatingIdempotentProcessor(List.of(processor(manager))),
                eventListener
        );
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        Thread.interrupted();
    }

    @Test
    @DisplayName("CT request when the key is seen for the first time should run the operation and store its result")
    void request_whenKeyIsSeenForFirstTime_shouldRunOperationAndStoreItsResult() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(row.result()).isEqualTo("charged");
            assertThat(row.expiresAt()).isEqualTo(clock.instant().plus(TTL));
        });
        assertThat(eventListener.successes).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the operation completes should let the claim go")
    void request_whenOperationCompletes_shouldLetClaimGo() throws Throwable {
        // when
        call(KEY, metadata().build(), ChargingOperation.returning("charged", ledger));

        // then
        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNull();
    }

    @Test
    @DisplayName("CT request when the same key comes back should replay the result without running the operation again")
    void request_whenSameKeyComesBack_shouldReplayResultWithoutRunningOperationAgain() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, metadata().build(), operation);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(eventListener.successes).isEqualTo(1);
        assertThat(eventListener.duplicates).isEqualTo(1);
        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNull();
    }

    @Test
    @DisplayName("CT request when the operation returned null should replay null without running it again")
    void request_whenOperationReturnedNull_shouldReplayNullWithoutRunningItAgain() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning(null, ledger);
        call(KEY, metadata().build(), operation);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isNull();
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY).orElseThrow().status()).isEqualTo(OperationStatus.PROCESSED);
    }

    @Test
    @DisplayName("CT request when different keys arrive should run the operation for each of them")
    void request_whenDifferentKeysArrive_shouldRunOperationForEachOfThem() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        call(KEY, metadata().build(), operation);
        call(OTHER_KEY, metadata().build(), operation);

        // then
        assertThat(operation.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the operation is replayed should tell the transport there is nothing new to record")
    void request_whenOperationIsReplayed_shouldTellTransportThereIsNothingNewToRecord() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        call(KEY, metadata().build(), operation);
        OperationState firstRun = channel.consume();
        call(KEY, metadata().build(), operation);
        OperationState replay = channel.consume();

        // then
        assertThat(firstRun.shouldRecordResponse()).isTrue();
        assertThat(replay.shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("CT request when another call holds the key and the call site rejects conflicts should be refused untouched")
    void request_whenAnotherCallHoldsKeyAndCallSiteRejects_shouldBeRefusedUntouched() {
        // given
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(OperationInProgressException.class);

        assertThat(operation.calls).isZero();
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(row.lockToken()).isEqualTo(FOREIGN_LOCK_TOKEN);
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.conflicts).isEqualTo(1);
        assertThat(eventListener.failures).isEmpty();
    }

    @Test
    @DisplayName("CT request when another call holds the key and the call site waits should replay the holder's result")
    void request_whenAnotherCallHoldsKeyAndCallSiteWaits_shouldReplayHoldersResult() throws Throwable {
        // given
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        repository.beforeFinds(() -> repository.completedByAnotherCall(KEY, "charged by holder", TTL));
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        Object result = call(KEY, metadata().conflictHandler(waitingHandler()).build(), operation);

        // then
        assertThat(result).isEqualTo("charged by holder");
        assertThat(operation.calls).isZero();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.conflicts).isEqualTo(1);
        assertThat(eventListener.successes).isZero();
    }

    @Test
    @DisplayName("CT request when the holder of the key died and its lease ran out should claim the key and run the operation")
    void request_whenHolderDiedAndLeaseRanOut_shouldClaimKeyAndRunOperation() throws Throwable {
        // given
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        clock.advance(LOCK_TTL);
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(row.lockToken()).isNull();
        });
    }

    @Test
    @DisplayName("CT request when the stored operation has outlived its ttl should run the operation again")
    void request_whenStoredOperationHasOutlivedItsTtl_shouldRunOperationAgain() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, metadata().build(), operation);
        clock.advance(TTL);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(2);
        assertThat(repository.row(KEY).orElseThrow().expiresAt()).isEqualTo(clock.instant().plus(TTL));
    }

    @Test
    @DisplayName("CT request when the same key comes back with a different request should be rejected and leave the record as it was")
    void request_whenSameKeyComesBackWithDifferentRequest_shouldBeRejectedAndLeaveRecordAsItWas() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, request("body"), fingerprinted(), operation);

        // when / then
        assertThatThrownBy(() -> call(KEY, request("other body"), fingerprinted(), operation))
                .isInstanceOf(FingerprintMismatchException.class);

        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(row.result()).isEqualTo("charged");
            assertThat(row.lockToken()).isNull();
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.failures).isEmpty();
    }

    @Test
    @DisplayName("CT request when the same key comes back with the same request should replay the result")
    void request_whenSameKeyComesBackWithSameRequest_shouldReplayResult() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, request("body"), fingerprinted(), operation);

        // when
        Object result = call(KEY, request("body"), fingerprinted(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when fingerprinting is on but the caller sends none should be rejected and let the claim go")
    void request_whenFingerprintingIsOnButCallerSendsNone_shouldBeRejectedAndLetClaimGo() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, request("body"), fingerprinted(), operation);

        // when / then
        assertThatThrownBy(() -> call(KEY, null, fingerprinted(), operation))
                .isInstanceOf(EmptyFingerprintException.class);

        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNull();
    }

    @Test
    @DisplayName("CT request when the operation fails with a type in recordFor should keep the failure on the record")
    void request_whenOperationFailsWithTypeInRecordFor_shouldKeepFailureOnRecord() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED);
            assertThat(row.exceptionClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(row.expiresAt()).isEqualTo(clock.instant().plus(TTL));
            assertThat(row.lockToken()).isNull();
        });
        assertThat(eventListener.exceptionallySuccesses).isEqualTo(1);
        assertThat(eventListener.successes).isZero();
        assertThat(eventListener.exceptions).isZero();
    }

    @Test
    @DisplayName("CT request when the failure is kept on the record should tell the transport to record the response")
    void request_whenFailureIsKeptOnRecord_shouldTellTransportToRecordResponse() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(channel.consume().shouldRecordResponse()).isTrue();
    }

    @Test
    @DisplayName("CT request when the same key comes back after a kept failure should rethrow it without running the operation")
    void request_whenSameKeyComesBackAfterKeptFailure_shouldRethrowItWithoutRunningOperation() {
        // given
        ChargingOperation operation = ChargingOperation.failing(new IllegalStateException("card declined"), ledger);
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation));

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("card declined");

        assertThat(operation.calls).isEqualTo(1);
        assertThat(eventListener.duplicates).isEqualTo(1);
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("CT request when a checked failure is kept should replay it as the very type the operation threw")
    void request_whenCheckedFailureIsKept_shouldReplayItAsTypeOperationThrew() {
        // given
        IOException thrown = new IOException("gateway timeout");
        ChargingOperation operation = ChargingOperation.failing(thrown, ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation)).isSameAs(thrown);
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(IOException.class)
                .hasMessage("gateway timeout");

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the kept failure has outlived its ttl should run the operation again")
    void request_whenKeptFailureHasOutlivedItsTtl_shouldRunOperationAgain() throws Throwable {
        // given
        assertThatThrownBy(() -> call(KEY, metadata().build(),
                ChargingOperation.failing(new IllegalStateException("card declined"), ledger)));
        clock.advance(TTL);
        ChargingOperation retried = ChargingOperation.returning("charged", ledger);

        // when
        Object result = call(KEY, metadata().build(), retried);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(retried.calls).isEqualTo(1);
        assertThat(repository.row(KEY).orElseThrow().status()).isEqualTo(OperationStatus.PROCESSED);
        assertThat(channel.consume().shouldRecordResponse()).isTrue();

        assertThat(call(KEY, metadata().build(), retried)).isEqualTo("charged");
        assertThat(retried.calls).isEqualTo(1);
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("CT request when the operation fails with a type outside recordFor should release the key for the next attempt")
    void request_whenOperationFailsWithTypeOutsideRecordFor_shouldReleaseKeyForNextAttempt() throws Throwable {
        // given
        IllegalStateException thrown = new IllegalStateException("gateway unavailable");
        OperationMetadata metadata = recordingFor(IllegalArgumentException.class);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata, ChargingOperation.failing(thrown, ledger))).isSameAs(thrown);

        assertThat(repository.row(KEY)).isEmpty();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.exceptions).isEqualTo(1);
        assertThat(eventListener.exceptionallySuccesses).isZero();

        ChargingOperation retried = ChargingOperation.returning("charged", ledger);
        assertThat(call(KEY, metadata, retried)).isEqualTo("charged");
        assertThat(call(KEY, metadata, retried)).isEqualTo("charged");
        assertThat(retried.calls).isEqualTo(1);
        assertThat(eventListener.failures).isEmpty();
    }

    @Test
    @DisplayName("CT request when recordFor names a supertype of the failure should keep it all the same")
    void request_whenRecordForNamesSupertypeOfFailure_shouldKeepItAllTheSame() {
        // given
        IllegalStateException thrown = new IllegalStateException("gateway unavailable");

        // when / then
        assertThatThrownBy(() -> call(KEY, recordingFor(RuntimeException.class), ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(repository.row(KEY)).hasValueSatisfying(row ->
                assertThat(row.status()).isEqualTo(OperationStatus.EXCEPTIONALLY_PROCESSED));
    }

    @Test
    @DisplayName("CT request when the claim was taken over while the operation ran should fail and leave the new holder's claim alone")
    void request_whenClaimWasTakenOverWhileOperationRan_shouldFailAndLeaveNewHoldersClaimAlone() {
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

        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(row.lockToken()).isEqualTo(FOREIGN_LOCK_TOKEN);
        });
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.successes).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.OUTCOME_NOT_RECORDED);
    }

    @Test
    @DisplayName("CT request when it meets another call's claim with a different request and waits should be rejected as a fingerprint mismatch")
    void request_whenItMeetsClaimWithDifferentRequestAndWaits_shouldBeRejectedAsFingerprintMismatch() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, request("body"), fingerprinted(), operation);
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        repository.beforeFinds(() -> repository.completedByAnotherCall(KEY, "charged", TTL));
        OperationMetadata metadata = metadata().fingerprintPolicy(fingerprintPolicy).conflictHandler(waitingHandler()).build();

        // when / then
        assertThatThrownBy(() -> call(KEY, request("other body"), metadata, operation))
                .isInstanceOf(FingerprintMismatchException.class);

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when it waits on a holder that reclaimed an expired record should abort instead of replaying the expired result")
    void request_whenItWaitsOnHolderThatReclaimedExpiredRecord_shouldAbortInsteadOfReplayingExpiredResult() throws Throwable {
        // given
        call(KEY, metadata().build(), ChargingOperation.returning("old charge", ledger));
        clock.advance(TTL);
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().conflictHandler(waitingHandler()).build(), operation))
                .isInstanceOf(WaitAbortedException.class)
                .hasMessageContaining(KEY.toString());

        assertThat(operation.calls).isZero();
        assertThat(repository.row(KEY).orElseThrow().lockToken()).isEqualTo(FOREIGN_LOCK_TOKEN);
    }

    @Test
    @DisplayName("CT request when it waits on a holder that kept a failure should rethrow that failure without running anything")
    void request_whenItWaitsOnHolderThatKeptFailure_shouldRethrowThatFailureWithoutRunningAnything() {
        // given
        repository.claimedByAnotherCall(KEY, FOREIGN_LOCK_TOKEN, LOCK_TTL);
        repository.beforeFinds(() -> repository.failedByAnotherCall(KEY, new IllegalStateException("card declined"), TTL));
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().conflictHandler(waitingHandler()).build(), operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("card declined");

        assertThat(operation.calls).isZero();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
    }

    @Test
    @DisplayName("CT request when the store fails to take the claim should run nothing and tell the transport nothing")
    void request_whenStoreFailsToTakeClaim_shouldRunNothingAndTellTransportNothing() {
        // given
        IllegalStateException storeDown = new IllegalStateException("store down");
        repository.failNext(InMemoryNonBlockingOperationRepository.Write.CLAIM, storeDown);
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(OperationLockFailedException.class)
                .hasCause(storeDown);

        assertThat(operation.calls).isZero();
        assertThat(repository.row(KEY)).isEmpty();
        assertThat(channel.consume()).isNull();
        assertThat(eventListener.failures).containsExactly(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("CT request when the transport fails to take the state should still answer and let the claim go")
    void request_whenTransportFailsToTakeState_shouldStillAnswerAndLetClaimGo() throws Throwable {
        // given
        channel.failNextPublish(new IllegalStateException("no request bound to thread"));

        // when
        Object result = call(KEY, metadata().build(), ChargingOperation.returning("charged", ledger));

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(eventListener.successes).isEqualTo(1);
        assertThat(eventListener.failures).isEmpty();
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(row.lockToken()).isNull();
        });
    }

    @Test
    @DisplayName("CT request when every event listener call fails should answer as if nobody listened")
    void request_whenEveryEventListenerCallFails_shouldAnswerAsIfNobodyListened() throws Throwable {
        // given
        eventListener.failEveryEvent();
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when
        Object first = call(KEY, metadata().build(), operation);
        Object replayed = call(KEY, metadata().build(), operation);

        // then
        assertThat(first).isEqualTo("charged");
        assertThat(replayed).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.row(KEY).orElseThrow().lockToken()).isNull();
    }

    @Test
    @DisplayName("CT request when recordFor names Throwable and the operation throws an Error should keep it on the record and rethrow it")
    void request_whenRecordForNamesThrowableAndOperationThrowsError_shouldKeepItOnRecordAndRethrowIt() {
        // given
        StackOverflowError thrown = new StackOverflowError("too deep");
        ChargingOperation operation = ChargingOperation.failing(thrown, ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation)).isSameAs(thrown);
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("too deep");

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when recordFor names nothing should release the key on any failure, an Error included")
    void request_whenRecordForNamesNothing_shouldReleaseKeyOnAnyFailureErrorIncluded() {
        // given
        StackOverflowError thrown = new StackOverflowError("too deep");

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().responseCacheConfig(ResponseConfig.defaults()).build(),
                ChargingOperation.failing(thrown, ledger)))
                .isSameAs(thrown);

        assertThat(repository.row(KEY)).isEmpty();
    }

    @Test
    @DisplayName("CT request when the first call sends no fingerprint should leave the key usable for the next proper request")
    void request_whenFirstCallSendsNoFingerprint_shouldLeaveKeyUsableForNextProperRequest() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        assertThatThrownBy(() -> call(KEY, null, fingerprinted(), operation))
                .isInstanceOf(EmptyFingerprintException.class);

        // when
        Object result = call(KEY, request("body"), fingerprinted(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the stored record expired and the request changed should run the operation for the new request")
    void request_whenStoredRecordExpiredAndRequestChanged_shouldRunOperationForNewRequest() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);
        call(KEY, request("body"), fingerprinted(), operation);
        clock.advance(TTL);

        // when
        Object result = call(KEY, request("other body"), fingerprinted(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when a kept failure meets a different request should be rejected as a fingerprint mismatch")
    void request_whenKeptFailureMeetsDifferentRequest_shouldBeRejectedAsFingerprintMismatch() {
        // given
        ChargingOperation operation = ChargingOperation.failing(new IllegalStateException("card declined"), ledger);
        assertThatThrownBy(() -> call(KEY, request("body"), fingerprinted(), operation))
                .isInstanceOf(IllegalStateException.class);

        // when / then
        assertThatThrownBy(() -> call(KEY, request("other body"), fingerprinted(), operation))
                .isInstanceOf(FingerprintMismatchException.class);

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when its own lease ran out but nobody took the key should still complete the operation")
    void request_whenOwnLeaseRanOutButNobodyTookKey_shouldStillCompleteOperation() throws Throwable {
        // given
        ChargingOperation operation = ChargingOperation.running(() -> {
            clock.advance(LOCK_TTL.multipliedBy(2));
            return "charged";
        }, ledger);

        // when
        Object result = call(KEY, metadata().build(), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(repository.row(KEY)).hasValueSatisfying(row -> {
            assertThat(row.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(row.lockToken()).isNull();
        });
    }

    @Test
    @DisplayName("CT request when the record disappears right after the claim should fail before running anything")
    void request_whenRecordDisappearsRightAfterClaim_shouldFailBeforeRunningAnything() {
        // given
        repository.beforeFinds(() -> repository.deletedByAnotherCall(KEY));
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(EarlyFailureException.class)
                .hasCauseInstanceOf(OperationDisappearedException.class);

        assertThat(operation.calls).isZero();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();
        assertThat(eventListener.successes).isZero();
        assertThat(eventListener.failures).containsExactly(FailureType.FAILED_BEFORE_RUN);
    }

    @Test
    @DisplayName("CT request when letting the claim go fails should still answer with the operation's result")
    void request_whenLettingClaimGoFails_shouldStillAnswerWithOperationsResult() throws Throwable {
        // given
        repository.failNext(InMemoryNonBlockingOperationRepository.Write.UNLOCK, new IllegalStateException("store down"));

        // when
        Object result = call(KEY, metadata().build(), ChargingOperation.returning("charged", ledger));

        // then
        assertThat(result).isEqualTo("charged");
    }

    @Test
    @DisplayName("CT request when the processor is called inside a transaction should refuse and touch nothing")
    void request_whenProcessorIsCalledInsideTransaction_shouldRefuseAndTouchNothing() {
        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);
        ChargingOperation operation = ChargingOperation.returning("charged", ledger);

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata().build(), operation))
                .isInstanceOf(ForeignTransactionException.class)
                .hasMessageContaining("must not run inside a transaction");

        assertThat(operation.calls).isZero();
        assertThat(repository.row(KEY)).isEmpty();
        assertThat(channel.consume()).isNull();
        assertThat(eventListener.failures).containsExactly(FailureType.MISPLACED);
    }

    @Test
    @DisplayName("CT request when the operation's nested call is refused should release the key though recordFor names Throwable")
    void request_whenOperationsNestedCallIsRefused_shouldReleaseKeyThoughRecordForNamesThrowable() throws Throwable {
        // given
        NestedIdempotencyException refusal = new NestedIdempotencyException();
        OperationMetadata metadata = recordingAll();

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata, ChargingOperation.failing(refusal, ledger))).isSameAs(refusal);

        assertThat(repository.row(KEY)).isEmpty();

        ChargingOperation retried = ChargingOperation.returning("charged", ledger);
        assertThat(call(KEY, metadata, retried)).isEqualTo("charged");
        assertThat(retried.calls).isEqualTo(1);
        assertThat(eventListener.failures).containsExactly(FailureType.MISPLACED);
        assertThat(eventListener.exceptions).isZero();
    }

    @Test
    @DisplayName("CT request when the operation is interrupted should release the key though recordFor names Throwable")
    void request_whenOperationIsInterrupted_shouldReleaseKeyThoughRecordForNamesThrowable() throws Throwable {
        // given
        InterruptedException interruption = new InterruptedException("shutting down");
        OperationMetadata metadata = recordingAll();

        // when / then
        assertThatThrownBy(() -> call(KEY, metadata, ChargingOperation.failing(interruption, ledger)))
                .isSameAs(interruption);

        assertThat(repository.row(KEY)).isEmpty();
        assertThat(channel.consume().shouldRecordResponse()).isFalse();

        ChargingOperation retried = ChargingOperation.returning("charged", ledger);
        assertThat(call(KEY, metadata, retried)).isEqualTo("charged");
        assertThat(retried.calls).isEqualTo(1);
        assertThat(eventListener.failures).containsExactly(FailureType.INTERRUPTED);
        assertThat(eventListener.exceptions).isZero();
    }

    @Test
    @DisplayName("CT request when the operation fails on an interrupted thread should release the key and leave the flag set")
    void request_whenOperationFailsOnInterruptedThread_shouldReleaseKeyAndLeaveFlagSet() {
        // given
        IllegalStateException failure = new IllegalStateException("gateway call aborted");
        ChargingOperation operation = ChargingOperation.running(
                () -> {
                    Thread.currentThread().interrupt();
                    throw failure;
                },
                ledger
        );

        // when / then
        assertThatThrownBy(() -> call(KEY, recordingAll(), operation)).isSameAs(failure);

        assertThat(repository.row(KEY)).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(eventListener.failures).containsExactly(FailureType.INTERRUPTED);
    }

    protected Object call(UUID idempotencyKey, OperationMetadata metadata, BusinessOperationCallback operation)
            throws Throwable {
        return call(idempotencyKey, request("body"), metadata, operation);
    }

    /**
     * Mimics what an entry-point module (aop, http) does per call: turn the request into a fingerprint and hand
     * the whole thing to the processor.
     */
    protected Object call(UUID idempotencyKey,
                          RequestContext request,
                          OperationMetadata metadata,
                          BusinessOperationCallback operation) throws Throwable {
        String fingerprint = request == null ? null : fingerprintPolicy.generate(request);
        return processor.process(
                new DefaultOperationContext(ResultType.ofClass(String.class), operation, idempotencyKey, fingerprint),
                metadata
        );
    }

    protected TestOperationMetadata.Builder metadata() {
        return TestOperationMetadata.builder()
                .processorType(processorType())
                .lockTtl(LOCK_TTL)
                .conflictHandler(new RejectConflictHandler())
                .responseCacheConfig(ResponseConfig.builder().recordFor(List.of(Throwable.class)).build());
    }

    private OperationMetadata fingerprinted() {
        return metadata().fingerprintPolicy(fingerprintPolicy).build();
    }

    protected OperationMetadata recordingFor(Class<? extends Throwable> type) {
        return metadata().responseCacheConfig(ResponseConfig.builder().recordFor(List.of(type)).build()).build();
    }

    private OperationMetadata recordingAll() {
        return metadata().responseCacheConfig(ResponseConfig.builder().recordFor(List.of(Throwable.class)).build()).build();
    }

    private ConflictHandler waitingHandler() {
        WaitConflictHandlerConfig config = WaitConflictHandlerConfig.builder()
                .delay(1)
                .multiplier(1)
                .maxAttempts(5)
                .maxDuration(Duration.ofSeconds(5))
                .build();
        return new WaitConflictHandler(config, repository, deserializer, clock);
    }

    private static RequestContext request(String body) {
        return TestRequestContext.of("/payments", "POST", body);
    }

    /**
     * The business data the operation writes to. It sits in the same transaction as the store when there is
     * one, so a rollback takes the charge away; without a transaction every charge stays.
     */
    protected static final class InMemoryLedger implements TransactionBoundResource {

        private final List<String> charges = new ArrayList<>();
        private int chargesAtBegin;
        private boolean inTransaction;

        void charge(String charge) {
            charges.add(charge);
        }

        List<String> charges() {
            return List.copyOf(charges);
        }

        @Override
        public void begin() {
            inTransaction = true;
            chargesAtBegin = charges.size();
        }

        @Override
        public void commit() {
            inTransaction = false;
        }

        @Override
        public void rollback() {
            if (inTransaction) {
                charges.subList(chargesAtBegin, charges.size()).clear();
            }
            inTransaction = false;
        }
    }

    /**
     * A business operation that writes a charge to the ledger before it returns or throws, so what it leaves
     * behind on failure is observable.
     */
    protected static final class ChargingOperation implements BusinessOperationCallback {

        private final BusinessOperationCallback body;
        private final InMemoryLedger ledger;
        protected int calls;

        private ChargingOperation(BusinessOperationCallback body, InMemoryLedger ledger) {
            this.body = body;
            this.ledger = ledger;
        }

        static ChargingOperation returning(String result, InMemoryLedger ledger) {
            return new ChargingOperation(() -> result, ledger);
        }

        static ChargingOperation failing(Throwable thrown, InMemoryLedger ledger) {
            return new ChargingOperation(() -> {
                throw thrown;
            }, ledger);
        }

        static ChargingOperation running(BusinessOperationCallback body, InMemoryLedger ledger) {
            return new ChargingOperation(body, ledger);
        }

        @Override
        public Object call() throws Throwable {
            calls++;
            ledger.charge("charge #" + calls);
            return body.call();
        }
    }

    protected static final class RecordingStateChannel implements OperationStateChannel {

        private final IntSupplier commits;
        private OperationState state;
        private RuntimeException nextPublishFailure;
        protected int commitsAtPublish;

        private RecordingStateChannel(IntSupplier commits) {
            this.commits = commits;
        }

        @Override
        public void publish(OperationState state) {
            if (nextPublishFailure != null) {
                RuntimeException failure = nextPublishFailure;
                nextPublishFailure = null;
                throw failure;
            }
            this.state = state;
            this.commitsAtPublish = commits.getAsInt();
        }

        void failNextPublish(RuntimeException failure) {
            this.nextPublishFailure = failure;
        }

        @Override
        public OperationState consume() {
            OperationState consumed = state;
            state = null;
            return consumed;
        }
    }

    protected static final class RecordingEventListener implements IdempotencyEventListener {

        protected int successes;
        protected int duplicates;
        protected int conflicts;
        protected int exceptions;
        protected int exceptionallySuccesses;
        protected final List<FailureType> failures = new ArrayList<>();
        private boolean failsEveryEvent;

        void failEveryEvent() {
            this.failsEveryEvent = true;
        }

        @Override
        public void onDuplicate() {
            duplicates++;
            failIfAsked();
        }

        @Override
        public void onConflict() {
            conflicts++;
            failIfAsked();
        }

        @Override
        public void onFingerprintMismatch() {}

        private void failIfAsked() {
            if (failsEveryEvent) {
                throw new IllegalStateException("listener down");
            }
        }

        @Override
        public void onBusinessFailure() {
            exceptions++;
            failIfAsked();
        }

        @Override
        public void onFailure(FailureType type) {
            failures.add(type);
            failIfAsked();
        }

        @Override
        public void onSuccess() {
            successes++;
            failIfAsked();
        }

        @Override
        public void onExceptionallySuccess() {
            exceptionallySuccesses++;
            failIfAsked();
        }
    }

    /**
     * Issues tokens in a known order, so a token read back from the store can be told from a foreign one.
     */
    private static final class SequentialLockTokenGenerator implements LockTokenGenerator {

        private long next = 1;

        @Override
        public UUID generate() {
            return new UUID(0, next++);
        }
    }

    /**
     * Stands in for idempify-jackson: results are text already, and a failure is kept as its message - which
     * is what {@link NonBlockingOperationDeserializer} rebuilds it from. The real serializer writes the whole
     * failure as JSON, so the replays of a kept failure here are green where they would not be (M72).
     */
    private static final class PassThroughResultSerializer implements ResultSerializer, ResultDeserializer {

        @Override
        public String serialize(Object result) {
            return result instanceof Throwable t ? t.getMessage() : (String) result;
        }

        @Override
        public Object deserialize(String rawResult, ResultType type) {
            return rawResult;
        }
    }

    /**
     * Stands in for idempify-jackson on the response half, which these tests never record.
     */
    private static final class NullResponseSerializer implements ResponseSerializer, ResponseDeserializer {

        @Override
        public String serialize(Response response) {
            return null;
        }

        @Override
        public Response deserialize(String rawResponse) {
            return null;
        }
    }
}
