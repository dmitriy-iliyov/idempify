package io.github.dmitriyiliyov.idempify.core;

import io.github.dmitriyiliyov.idempify.core.fingerprint.*;
import io.github.dmitriyiliyov.idempify.core.request.RequestContext;
import io.github.dmitriyiliyov.idempify.core.response.OperationState;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
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

    private final InMemoryOperationRepository repository = new InMemoryOperationRepository();
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final RecordingStateChannel channel = new RecordingStateChannel(() -> transactionManager.commits);
    private final RecordingEventListener eventListener = new RecordingEventListener();
    private final FingerprintPolicy fingerprintPolicy = new RawHashingFingerprintPolicy(new ThrowingEmptyBodyFallback());

    private TestClock clock;
    private IdempotentProcessor processor;

    @BeforeEach
    void setUp() {
        clock = TestClock.fixedAt(TestClock.EPOCH);
        TransactionalOperationManager operationManager = new DefaultTransactionalOperationManager(
                new DefaultOperationMapper(),
                repository,
                new DefaultFingerprintMatcher(IdempotencyEventListener.NOOP),
                new PassThroughResultSerializer(),
                new PassThroughResultSerializer(),
                clock
        );
        processor = new DelegatingIdempotentProcessor(List.of(new TransactionalIdempotentProcessor(
                new TransactionTemplate(transactionManager),
                operationManager,
                channel,
                eventListener
        )));
    }

    @Test
    @DisplayName("CT request when the key is seen for the first time should run the operation and store its result")
    void request_whenKeyIsSeenForFirstTime_shouldRunOperationAndStoreItsResult() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        String result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
        assertThat(repository.findByIdempotencyKey(KEY)).hasValueSatisfying(stored -> {
            assertThat(stored.getStatus()).isEqualTo(OperationStatus.PROCESSED);
            assertThat(stored.getResult()).isEqualTo("charged");
        });
    }

    @Test
    @DisplayName("CT request when the same key comes back should be answered from the store without running the operation again")
    void request_whenSameKeyComesBack_shouldBeAnsweredFromStoreWithoutRunningOperationAgain() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        String result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the same key comes back should be counted as a duplicate and not as a second success")
    void request_whenSameKeyComesBack_shouldBeCountedAsDuplicateAndNotAsSecondSuccess() {
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
    void request_whenDifferentKeysArrive_shouldRunOperationForEachOfThem() {
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
    void request_whenSameKeyComesBackWithSameRequest_shouldBeAnsweredFromStore() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(true), operation);

        // when
        String result = call(KEY, request("body"), metadata(true), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the same key comes back with a different request should be rejected as a fingerprint mismatch")
    void request_whenSameKeyComesBackWithDifferentRequest_shouldBeRejectedAsFingerprintMismatch() {
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
    void request_whenFingerprintingIsOnButCallerSendsNone_shouldBeRejectedAsInvalidFingerprint() {
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
    void request_whenFingerprintingIsOff_shouldAnswerChangedRequestFromStoreAllTheSame() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        String result = call(KEY, request("other body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the stored operation has outlived its ttl should run the operation again")
    void request_whenStoredOperationHasOutlivedItsTtl_shouldRunOperationAgain() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");
        call(KEY, request("body"), metadata(false), operation);

        // when
        clock.advance(Duration.ofHours(25));
        String result = call(KEY, request("body"), metadata(false), operation);

        // then
        assertThat(result).isEqualTo("charged");
        assertThat(operation.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CT request when the operation ran longer than its own ttl should still be replayable afterwards")
    void request_whenOperationRanLongerThanItsOwnTtl_shouldStillBeReplayableAfterwards() {
        // given
        Duration ttl = Duration.parse(IdempifyDefaults.TTL_VALUE);
        SlowOperation slow = new SlowOperation("charged", clock, ttl.plus(Duration.ofHours(1)));

        // when
        call(KEY, request("body"), metadata(false), slow);
        String replayed = call(KEY, request("body"), metadata(false), slow);

        // then
        assertThat(replayed).isEqualTo("charged");
        assertThat(slow.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("CT request when the operation ran longer than its own ttl should date the expiry from the result")
    void request_whenOperationRanLongerThanItsOwnTtl_shouldDateExpiryFromResult() {
        // given
        Duration ttl = Duration.parse(IdempifyDefaults.TTL_VALUE);
        Duration ranFor = ttl.plus(Duration.ofHours(1));
        SlowOperation slow = new SlowOperation("charged", clock, ranFor);

        // when
        call(KEY, request("body"), metadata(false), slow);

        // then
        assertThat(channel.consume().getExpiresAt())
                .isEqualTo(TestClock.EPOCH.plus(ranFor).plus(ttl));
    }

    @Test
    @DisplayName("CT request while the operation is still running should hold a row that carries no expiry")
    void request_whileOperationIsStillRunning_shouldHoldRowThatCarriesNoExpiry() {
        // given
        List<Operation> seenMidFlight = new ArrayList<>();
        ExternalOperationCallback<String> peeking = () -> {
            seenMidFlight.add(repository.findByIdempotencyKey(KEY).orElseThrow());
            return "charged";
        };

        // when
        call(KEY, request("body"), metadata(false), peeking);

        // then
        assertThat(seenMidFlight).singleElement().satisfies(midFlight -> {
            assertThat(midFlight.getStatus()).isEqualTo(OperationStatus.IN_PROCESS);
            assertThat(midFlight.getExpiresAt()).isNull();
        });
        assertThat(repository.findByIdempotencyKey(KEY).orElseThrow().getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("CT request when the operation is replayed should tell the transport it was replayed")
    void request_whenOperationIsReplayed_shouldTellTransportItWasReplayed() {
        // given
        RecordingOperation operation = new RecordingOperation("charged");

        // when
        call(KEY, request("body"), metadata(false), operation);
        OperationState firstRun = channel.consume();
        call(KEY, request("body"), metadata(false), operation);
        OperationState replay = channel.consume();

        // then
        assertThat(firstRun.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.getExpiresAt())
                .isEqualTo(TestClock.EPOCH.plus(Duration.parse(IdempifyDefaults.TTL_VALUE)));
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
        assertThat(repository.findByIdempotencyKey(KEY))
                .hasValueSatisfying(stored -> assertThat(stored.getStatus()).isEqualTo(OperationStatus.IN_PROCESS));
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
    void request_whenOperationCompletes_shouldTellTransportOnlyAfterTransactionCommitted() {
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
                .processorType(ProcessorType.LOCK_BASED)
                .build();

        // when / then
        assertThatThrownBy(() -> call(KEY, request("body"), metadata, operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no processor found");

        assertThat(operation.calls).isZero();
        assertThat(repository.findByIdempotencyKey(KEY)).isEmpty();
    }

    /**
     * Mimics what an entry-point module (aop, http) does per call: turn the request into a fingerprint and hand
     * the whole thing to the processor.
     */
    private String call(UUID idempotencyKey,
                        RequestContext request,
                        OperationMetadata metadata,
                        ExternalOperationCallback<String> operation) {
        String fingerprint = request == null ? null : fingerprintPolicy.generate(request);
        return processor.process(
                new DefaultOperationContext<>(String.class, operation, idempotencyKey, fingerprint),
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
     * the two updates are compare-and-swap on the current status.
     */
    private static final class InMemoryOperationRepository implements TransactionalOperationRepository {

        private final Map<UUID, Operation> rows = new HashMap<>();

        @Override
        public Operation saveIfAbsent(Operation operation) {
            Operation stored = rows.get(operation.getIdempotencyKey());
            if (stored == null) {
                rows.put(operation.getIdempotencyKey(), copyOf(operation));
                return copyOf(operation);
            }
            stored.setFirstAttempt(false);
            return copyOf(stored);
        }

        @Override
        public Operation update(Operation operation, OperationStatus onStatus) {
            Operation stored = rows.get(operation.getIdempotencyKey());
            if (stored == null || stored.getStatus() != onStatus) {
                throw new OperationStatusMismatchException(operation.getIdempotencyKey(), onStatus);
            }
            rows.put(operation.getIdempotencyKey(), copyOf(operation));
            return copyOf(operation);
        }

        @Override
        public Operation saveResultAndUpdateStatus(String result,
                                                   OperationStatus status,
                                                   Instant expiresAt,
                                                   UUID idempotencyKey,
                                                   OperationStatus onStatus) {
            Operation stored = rows.get(idempotencyKey);
            if (stored == null || stored.getStatus() != onStatus) {
                throw new OperationStatusMismatchException(idempotencyKey, onStatus);
            }
            stored.setResult(result);
            stored.setStatus(status);
            stored.setExpiresAt(expiresAt);
            return copyOf(stored);
        }

        @Override
        public Optional<Operation> findByIdempotencyKey(UUID idempotencyKey) {
            return Optional.ofNullable(rows.get(idempotencyKey)).map(InMemoryOperationRepository::copyOf);
        }

        private static Operation copyOf(Operation operation) {
            return new Operation(
                    operation.getIdempotencyKey(),
                    operation.getStatus(),
                    operation.isFirstAttempt(),
                    operation.getResult(),
                    operation.getFingerprint(),
                    operation.getExpiresAt(),
                    operation.getCreatedAt()
            );
        }
    }

    /**
     * Stands in for idempify-jackson: core stores results as text, and these tests deal in text already.
     */
    private static final class PassThroughResultSerializer implements ResultSerializer, ResultDeserializer {

        @Override
        public <T> String serialize(T result) {
            return (String) result;
        }

        @Override
        public <T> T deserialize(String rawResult, Class<T> type) {
            return type.cast(rawResult);
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

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
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
    }

    private static final class RecordingOperation implements ExternalOperationCallback<String> {

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
    private static final class SlowOperation implements ExternalOperationCallback<String> {

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

    private record FailingOperation(Throwable thrown) implements ExternalOperationCallback<String> {

        @Override
        public String call() throws Throwable {
            throw thrown;
        }
    }
}
