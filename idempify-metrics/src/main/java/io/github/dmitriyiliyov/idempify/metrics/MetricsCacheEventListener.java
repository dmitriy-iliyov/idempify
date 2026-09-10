package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.cache.CacheEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

public class MetricsCacheEventListener implements CacheEventListener {

    private static final String GETS_METER = "idempify.cache.gets";
    private static final String RESULT_TAG = "result";
    private static final String GETS_DESCRIPTION = "Operation cache lookups, by what they found";
    private final Counter hitCounter;
    private final Counter missCounter;

    public MetricsCacheEventListener(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        this.hitCounter = resultCounter(registry, "hit");
        this.missCounter = resultCounter(registry, "miss");
    }

    @Override
    public void onHit() {
        hitCounter.increment();
    }

    @Override
    public void onMiss() {
        missCounter.increment();
    }

    private static Counter resultCounter(MeterRegistry registry, String result) {
        return Counter.builder(GETS_METER)
                .description(GETS_DESCRIPTION)
                .tag(RESULT_TAG, result)
                .register(registry);
    }
}
