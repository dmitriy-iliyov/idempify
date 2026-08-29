package io.github.dmitriyiliyov.idempify.metrics;

import io.github.dmitriyiliyov.idempify.core.response.AbstractResponseCacheDecorator;
import io.github.dmitriyiliyov.idempify.core.response.CachedResponse;
import io.github.dmitriyiliyov.idempify.core.response.ResponseCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.UUID;

public class MetricsResponseCacheDecorator extends AbstractResponseCacheDecorator {

    private static final String GETS_METER = "idempify.cache.gets";
    private static final String RESULT_TAG = "result";
    private static final String GETS_DESCRIPTION = "Response cache lookups, by what they found";
    private final Counter hitCounter;
    private final Counter missCounter;

    public MetricsResponseCacheDecorator(ResponseCache delegate, MeterRegistry registry) {
        super(delegate);
        Objects.requireNonNull(registry, "registry cannot be null");
        this.hitCounter = resultCounter(registry, "hit");
        this.missCounter = resultCounter(registry, "miss");
    }

    @Override
    public CachedResponse findByIdempotencyKey(UUID idempotencyKey) {
        CachedResponse response = super.findByIdempotencyKey(idempotencyKey);
        if (response == null) {
            missCounter.increment();
        } else {
            hitCounter.increment();
        }
        return response;
    }

    private static Counter resultCounter(MeterRegistry registry, String result) {
        return Counter.builder(GETS_METER)
                .description(GETS_DESCRIPTION)
                .tag(RESULT_TAG, result)
                .register(registry);
    }
}
