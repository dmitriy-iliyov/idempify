package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.IdempotencyEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

public class MetricsIdempotencyEventListener implements IdempotencyEventListener {

    private static final String OPERATIONS_METER = "idempify.operations";
    private static final String OUTCOME_TAG = "outcome";
    private static final String OPERATIONS_DESCRIPTION = "Idempotent calls, by the outcome they reached";
    private final Counter duplicateCounter;
    private final Counter conflictCounter;
    private final Counter fingerprintMismatchCounter;
    private final Counter exceptionCounter;
    private final Counter successCounter;

    public MetricsIdempotencyEventListener(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        this.duplicateCounter = outcomeCounter(registry, "duplicate");
        this.conflictCounter = outcomeCounter(registry, "conflict");
        this.fingerprintMismatchCounter = outcomeCounter(registry, "fingerprint-mismatch");
        this.exceptionCounter = outcomeCounter(registry, "exception");
        this.successCounter = outcomeCounter(registry, "success");
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
    public void onException() {
        exceptionCounter.increment();
    }

    @Override
    public void onSuccess() {
        successCounter.increment();
    }

    private static Counter outcomeCounter(MeterRegistry registry, String outcome) {
        return Counter.builder(OPERATIONS_METER)
                .description(OPERATIONS_DESCRIPTION)
                .tag(OUTCOME_TAG, outcome)
                .register(registry);
    }
}
