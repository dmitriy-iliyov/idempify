package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.FailureType;
import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class MetricsIdempotencyEventListener implements IdempotencyEventListener {

    private static final String OPERATIONS_METER = "idempify.operations";
    private static final String OUTCOME_TAG = "outcome";
    private static final String FAILURE_TAG = "failure";
    private static final String NO_FAILURE = "none";
    private static final String OPERATIONS_DESCRIPTION = "Idempotent calls, by the outcome they reached";
    private final Counter duplicateCounter;
    private final Counter conflictCounter;
    private final Counter fingerprintMismatchCounter;
    private final Counter businessFailureCounter;
    private final Counter successCounter;
    private final Counter exceptionallyProcessedCounter;
    private final Map<FailureType, Counter> failureCounters;

    public MetricsIdempotencyEventListener(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        this.duplicateCounter = outcomeCounter(registry, "duplicate");
        this.conflictCounter = outcomeCounter(registry, "conflict");
        this.fingerprintMismatchCounter = outcomeCounter(registry, "fingerprint-mismatch");
        this.businessFailureCounter = outcomeCounter(registry, "business-failure");
        this.successCounter = outcomeCounter(registry, "success");
        this.exceptionallyProcessedCounter = outcomeCounter(registry, "exceptionally-processed");
        this.failureCounters = new EnumMap<>(FailureType.class);
        for (FailureType type : FailureType.values()) {
            failureCounters.put(type, failureCounter(registry, type));
        }
    }

    private static Counter outcomeCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(OPERATIONS_METER)
                .description(OPERATIONS_DESCRIPTION)
                .tag(OUTCOME_TAG, outcome)
                .tag(FAILURE_TAG, NO_FAILURE)
                .register(registry);
    }

    private static Counter failureCounter(MeterRegistry registry, FailureType type) {
        return Counter.builder(OPERATIONS_METER)
                .description(OPERATIONS_DESCRIPTION)
                .tag(OUTCOME_TAG, "failure")
                .tag(FAILURE_TAG, type.name().toLowerCase().replace('_', '-'))
                .register(registry);
    }

    @Override
    public void onDuplicate() {
        duplicateCounter.increment();
    }

    @Override
    public void onConflict() {
        conflictCounter.increment();
    }

    @Override
    public void onFingerprintMismatch() {
        fingerprintMismatchCounter.increment();
    }

    @Override
    public void onBusinessFailure() {
        businessFailureCounter.increment();
    }

    @Override
    public void onFailure(FailureType type) {
        failureCounters.get(type).increment();
    }

    @Override
    public void onSuccess() {
        successCounter.increment();
    }

    @Override
    public void onExceptionallySuccess() {
        exceptionallyProcessedCounter.increment();
    }
}
