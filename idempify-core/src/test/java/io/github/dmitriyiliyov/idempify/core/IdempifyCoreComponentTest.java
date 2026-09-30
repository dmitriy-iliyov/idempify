package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.response.Response;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the whole core chain — processor, operation manager, mapper, fingerprint policy and matcher — against
 * stand-ins for the modules core does not own: the repository (idempify-postgresql), the result (de)serializer
 * (idempify-jackson), the state channel and the request context (idempify-http).
 */
class IdempifyCoreComponentTest {

    private static final UUID KEY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_KEY = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final TestClock clock = TestClock.fixedAt(TestClock.EPOCH);
    private final InMemoryOperationRepository repository = new InMemoryOperationRepository(clock);
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final RecordingStateChannel channel = new RecordingStateChannel(() -> transactionManager.commits);
    private final RecordingEventListener eventListener = new RecordingEventListener();
    private final FingerprintPolicy fingerprintPolicy = new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());

    private IdempotentProcessor processor;

    @BeforeEach
    void setUp() {
        PassThroughResultSerializer resultSerializer = new PassThroughResultSerializer();
        PassThroughResponseSerializer responseSerializer = new PassThroughResponseSerializer();
        BlockingOperationManager operationManager = new DefaultBlockingOperationManager(
                repository,
                new DefaultFingerprintMatcher(IdempotencyEventListener.NOOP),
                new DefaultOperationSerializer(resultSerializer, responseSerializer),
                new DefaultOperationDeserializer(resultSerializer, responseSerializer),
                resultSerializer
        );
        processor = new DelegatingIdempotentProcessor(List.of(new BlockingIdempotentProcessor(
                new TransactionTemplate(transactionManager),
                operationManager,
                channel,
                eventListener
        )));
    }

    @Test
    @DisplayName("CT request when the key is seen for the first time should run the operation and store its result")
    void request_whenKeyIsSeenForFirstTime_shouldRunOperationAndStoreItsResult() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        Object result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.find(KEY)).hasValueSatisfying(stored -> {
            assertThat(stored.status()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(stored.result()).isEqualTo("charged");
        });
    }

    @Test
    @DisplayName("CT request when the same key comes back should be answered from the store without running the operation again")
    void request_whenSameKeyComesBack_shouldBeAnsweredFromStoreWithoutRunningOperationAgain() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        Object result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the same key comes back should be counted as a duplicate and not as a second success")
    void request_whenSameKeyComesBack_shouldBeCountedAsDuplicateAndNotAsSecondSuccess() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(eventListener.successes).isEqualTo(1);
        assertThat(eventListener.duplicates).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when different keys arrive should run the operation for each of them")
    void request_whenDifferentKeysArrive_shouldRunOperationForEachOfThem() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        call(KEY, request("body"), metadata(false), operation);
        call(OTHER_KEY, request("body"), metadata(false), operation);

        // then
        assertThat(operation.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the same key comes back with the same request should be answered from the store")
    void request_whenSameKeyComesBackWithSameRequest_shouldBeAnsweredFromStore() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(true), operation);

        // when
        Object result = call(KEY, request("body"), metadata(true), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the same key comes back with a different request should be rejected as a fingerprint mismatch")
    void request_whenSameKeyComesBackWithDifferentRequest_shouldBeRejectedAsFingerprintMismatch() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(true), operation);

        // when / then
        assertThatThrownBy(() -> call(KEY, request("other body"), metadata(true), operation))
                .isInstanceOf(FingerprintMismatchException.class);

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when fingerprinting is on but the caller sends none should be rejected as an invalid fingerprint")
    void request_whenFingerprintingIsOnButCallerSendsNone_shouldBeRejectedAsInvalidFingerprint() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(true), operation);

        // when / then
        assertThatThrownBy(() -> call(KEY, null, metadata(true), operation))
                .isInstanceOf(InvalidFingerprintException.class);

        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when fingerprinting is off should answer a changed request from the store all the same")
    void request_whenFingerprintingIsOff_shouldAnswerChangedRequestFromStoreAllTheSame() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        Object result = call(KEY, request("other body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the stored operation has outlived its ttl should run the operation again")
    void request_whenStoredOperationHasOutlivedItsTtl_shouldRunOperationAgain() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        clock.advance(Duration.ofHours(25));
        Object result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the operation ran longer than its own ttl should still be replayable afterwards")
    void request_whenOperationRanLongerThanItsOwnTtl_shouldStillBeReplayableAfterwards() throws Throwable {
        // given
        Duration ttl = Duration.parse(IdempifyDefaults.TTL_VALUE);
        SlowOperation slow = new SlowOperation("charged", clock, ttl.plus(Duration.ofHours(1)));

        // when
        call(KEY, request("body"), metadata(false), slow);
        Object replayed = call(KEY, request("body"), metadata(false), slow);

        // then
        assertThat(replayed).isEqualTo("charged");
        assertThat(slow.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the operation ran longer than its own ttl should date the expiry from the result")
    void request_whenOperationRanLongerThanItsOwnTtl_shouldDateExpiryFromResult() throws Throwable {
        // given
        Duration ttl = Duration.parse(IdempifyDefaults.TTL_VALUE);
        SlowOperation slow = new SlowOperation("charged", clock, ttl.plus(Duration.ofHours(1)));

        // when
        call(KEY, request("body"), metadata(false), slow);

        // then
        assertThat(repository.find(KEY).orElseThrow().remainingMillis()).isEqualTo(ttl.toMillis());
    }

    @Test
    @DisplayName("CT request while the operation is still running should hold a row that carries no expiry")
    void request_whileOperationIsStillRunning_shouldHoldRowThatCarriesNoExpiry() throws Throwable {
        // given
        List<RawOperation> seenMidFlight = new ArrayList<>();
        ExternalOperationCallback peeking = () -> {
            seenMidFlight.add(repository.find(KEY).orElseThrow());
            return "charged";
        };

        // when
        call(KEY, request("body"), metadata(false), peeking);

        // then
        assertThat(seenMidFlight).singleElement().satisfies(midFlight -> {
            assertThat(midFlight.status()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(midFlight.remainingMillis()).isNull();
        });
        assertThat(repository.find(KEY).orElseThrow().remainingMillis()).isNotNull();
    }

    @Test
    @DisplayName("CT request when the operation is replayed should tell the transport there is nothing new to record")
    void request_whenOperationIsReplayed_shouldTellTransportThereIsNothingNewToRecord() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        call(KEY, request("body"), metadata(false), operation);
        OperationState firstRun = channel.consume();
        call(KEY, request("body"), metadata(false), operation);
        OperationState replay = channel.consume();

        // then
        assertThat(firstRun.shouldRecordResponse()).isTrue();
        assertThat(replay.shouldRecordResponse()).isFalse();
        assertThat(repository.find(KEY).orElseThrow().remainingMillis())
                .isEqualTo(Duration.parse(IdempifyDefaults.TTL_VALUE).toMillis());
    }

    @Test
    @DisplayName("CT request when the operation fails should roll the transaction back and store no result")
    void request_whenOperationFails_shouldRollTransactionBackAndStoreNoResult() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, request("body"), metadata(false), new FailingOperation(thrown)))
                .isSameAs(thrown);

        assertThat(transactionManager.rollbacks).isEqualTo(1);
        assertThat(repository.find(KEY))
                .hasValueSatisfying(stored -> assertThat(stored.status()).isEqualTo(OperationStatus.IN_PROCESS));
    }

    @Test
    @DisplayName("CT request when the transaction is rolled back should publish no state and count no success")
    void request_whenTransactionIsRolledBack_shouldPublishNoStateAndCountNoSuccess() {
        // given
        IllegalStateException thrown = new IllegalStateException("card declined");

        // when / then
        assertThatThrownBy(() -> call(KEY, request("body"), metadata(false), new FailingOperation(thrown)))
                .isSameAs(thrown);

        assertThat(transactionManager.rollbacks).isEqualTo(1);
        assertThat(channel.consume()).isNull();
        assertThat(eventListener.successes).isZero();
    }

    @Test
    @DisplayName("CT request when the operation completes should tell the transport only after the transaction committed")
    void request_whenOperationCompletes_shouldTellTransportOnlyAfterTransactionCommitted() throws Throwable {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(channel.commitsAtPublish).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the metadata names a processor nobody serves should fail before touching the store")
    void request_whenMetadataNamesProcessorNobodyServes_shouldFailBeforeTouchingStore() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        OperationMetadata metadata = TestOperationMetadata.builder()
                .processorType(ProcessorType.NON_BLOCKING)
                .build();

        // when / then
        assertThatThrownBy(() -> call(KEY, request("body"), metadata, operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no processor found");

        assertThat(operation.calls).isZero();
        assertThat(repository.find(KEY)).isEmpty();
    }

    @Test
    @DisplayName("CT request when the processor joined somebody else's transaction should refuse and touch nothing")
    void request_whenProcessorJoinedSomebodyElsesTransaction_shouldRefuseAndTouchNothing() {
        // given
        transactionManager.joinsExistingTransaction = true;
        RecordingOperation operation = new RecordingOperation("charged");

        // when / then
        assertThatThrownBy(() -> call(KEY, request("body"), metadata(false), operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must own its transaction");

        assertThat(operation.calls).isZero();
        assertThat(repository.find(KEY)).isEmpty();
        assertThat(channel.consume()).isNull();
        assertThat(eventListener.successes).isZero();
    }

    /**
     * Mimics what an entry-point module (aop, http) does per call: turn the request into a fingerprint and hand
     * the whole thing to the processor.
     */
    private Object call(UUID idempotencyKey,
                        RequestContext request,
                        OperationMetadata metadata,
                        ExternalOperationCallback operation) throws Throwable {
        String fingerprint = request == null ? null : fingerprintPolicy.generate(request);
        return processor.process(
                new DefaultOperationContext(ResultType.ofClass(String.class), operation, idempotencyKey, fingerprint),
                metadata
        );
    }

    private RequestContext request(String body) {
        return TestRequestContext.of("/payments", "POST", body);
    }

    private OperationMetadata metadata(boolean useFingerprint) {
        return TestOperationMetadata.builder()
                .fingerprintPolicy(useFingerprint ? fingerprintPolicy : null)
                .build();
    }

    /**
     * Stands in for idempify-postgresql, holding to the same contract: {@code saveIfAbsent} is first-writer-wins,
     * the two updates are compare-and-swap on the current status, and the expiry is the store's own - counted
     * from its clock when the result is saved and read back as the time a row has left.
     */
    private static final class InMemoryOperationRepository implements BlockingOperationRepository {

        private final TestClock clock;
        private final Map<UUID, RawOperation> rows = new HashMap<>();
        private final Map<UUID, Instant> expiries = new HashMap<>();

        private InMemoryOperationRepository(TestClock clock) {
            this.clock = clock;
        }

        @Override
        public RawOperation saveIfAbsent(RawOperation operation) {
            RawOperation stored = rows.get(operation.idempotencyKey());
            if (stored == null) {
                rows.put(operation.idempotencyKey(), operation);
                return read(operation);
            }

            RawOperation repeated = new RawOperation(
                    stored.idempotencyKey(),
                    stored.status(),
                    false,
                    stored.result(),
                    stored.response(),
                    stored.fingerprint(),
                    null,
                    stored.createdAt()
            );
            rows.put(repeated.idempotencyKey(), repeated);
            return read(repeated);
        }

        @Override
        public RawOperation update(RawOperation operation, OperationStatus onStatus) {
            RawOperation stored = rows.get(operation.idempotencyKey());
            if (stored == null || stored.status() != onStatus) {
                throw new OperationStatusMismatchException(operation.idempotencyKey(), onStatus);
            }
            rows.put(operation.idempotencyKey(), operation);
            expiries.remove(operation.idempotencyKey());
            return read(operation);
        }

        @Override
        public void saveResultAndUpdateStatus(UUID idempotencyKey,
                                              String result,
                                              OperationStatus status,
                                              long ttlMillis,
                                              OperationStatus onStatus) {
            RawOperation stored = rows.get(idempotencyKey);
            if (stored == null || stored.status() != onStatus) {
                throw new OperationStatusMismatchException(idempotencyKey, onStatus);
            }

            RawOperation completed = new RawOperation(
                    stored.idempotencyKey(),
                    status,
                    stored.isFirstAttempt(),
                    result,
                    stored.response(),
                    stored.fingerprint(),
                    null,
                    stored.createdAt()
            );
            rows.put(idempotencyKey, completed);
            expiries.put(idempotencyKey, clock.instant().plusMillis(ttlMillis));
        }

        private Optional<RawOperation> find(UUID idempotencyKey) {
            return Optional.ofNullable(rows.get(idempotencyKey)).map(this::read);
        }

        private RawOperation read(RawOperation row) {
            Instant expiresAt = expiries.get(row.idempotencyKey());
            return new RawOperation(
                    row.idempotencyKey(),
                    row.status(),
                    row.isFirstAttempt(),
                    row.result(),
                    row.response(),
                    row.fingerprint(),
                    expiresAt == null ? null : expiresAt.toEpochMilli() - clock.instant().toEpochMilli(),
                    row.createdAt()
            );
        }
    }

    /**
     * Stands in for idempify-jackson on the response half: these tests never record one, so the pair only has
     * to keep the round trip honest about nothing being there.
     */
    private static final class PassThroughResponseSerializer implements ResponseSerializer, ResponseDeserializer {

        @Override
        public String serialize(Response response) {
            return null;
        }

        @Override
        public Response deserialize(String rawResponse) {
            return null;
        }
    }

    /**
     * Stands in for idempify-jackson: core stores results as text, and these tests deal in text already.
     */
    private static final class PassThroughResultSerializer implements ResultSerializer, ResultDeserializer {

        @Override
        public String serialize(Object result) {
            return (String) result;
        }

        @Override
        public Object deserialize(String rawResult, ResultType type) {
            return rawResult;
        }
    }

    private static final class RecordingStateChannel implements OperationStateChannel {

        private final IntSupplier commits;
        private OperationState state;
        private int commitsAtPublish;

        private RecordingStateChannel(IntSupplier commits) {
            this.commits = commits;
        }

        @Override
        public void publish(OperationState state) {
            this.state = state;
            this.commitsAtPublish = commits.getAsInt();
        }

        @Override
        public OperationState consume() {
            OperationState consumed = state;
            state = null;
            return consumed;
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {

        private int commits;
        private int rollbacks;
        private boolean joinsExistingTransaction;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus(!joinsExistingTransaction);
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }

    private static final class RecordingEventListener implements IdempotencyEventListener {

        private int successes;
        private int duplicates;
        private int exceptions;

        @Override
        public void onDuplicate() {
            duplicates++;
        }

        @Override
        public void onConflict() {}

        @Override
        public void onFingerprintMismatch() {}

        @Override
        public void onException() {
            exceptions++;
        }

        @Override
        public void onSuccess() {
            successes++;
        }

        @Override
        public void onExceptionallySuccess() {}
    }

    private static final class RecordingOperation implements ExternalOperationCallback {

        private final String result;
        private int calls;

        private RecordingOperation(String result) {
            this.result = result;
        }

        @Override
        public String call() {
            calls++;
            return result;
        }
    }

    /**
     * An operation that takes time: it moves the clock forward before returning, which is how the expiry
     * being counted from completion rather than from the claim becomes observable at all.
     */
    private static final class SlowOperation implements ExternalOperationCallback {

        private final String result;
        private final TestClock clock;
        private final Duration duration;
        private int calls;

        private SlowOperation(String result, TestClock clock, Duration duration) {
            this.result = result;
            this.clock = clock;
            this.duration = duration;
        }

        @Override
        public String call() {
            calls++;
            clock.advance(duration);
            return result;
        }
    }

    private record FailingOperation(Throwable thrown) implements ExternalOperationCallback {

        @Override
        public String call() throws Throwable {
            throw thrown;
        }
    }
}
