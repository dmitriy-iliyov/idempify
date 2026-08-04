package io.github.dmitriyiliyov.idempify.aop;

import io.github.dmitriyiliyov.idempify.core.DefaultRawOperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadata;
import io.github.dmitriyiliyov.idempify.core.OperationMetadataManager;
import io.github.dmitriyiliyov.idempify.core.RawOperationMetadata;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.util.Objects;

public class DefaultOperationMetadataFactory implements OperationMetadataFactory {

    private final OperationMetadataCache cache;
    private final OperationMetadataManager manager;
    private final Object lock = new Object();

    public DefaultOperationMetadataFactory(OperationMetadataCache cache, OperationMetadataManager manager) {
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    @Override
    public OperationMetadata generate(Idempotent annotation, ProceedingJoinPoint jp) {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        Object target = jp.getTarget();
        method = AopUtils.getMostSpecificMethod(method, target == null ? null : target.getClass());

        OperationMetadata metadata = cache.get(method);
        if (metadata != null) {
            return metadata;
        }

        RawOperationMetadata rawMetadata = buildMetadata(annotation);

        synchronized (lock) {
            metadata = cache.get(method);

            if (metadata != null) {
                return metadata;
            }

            metadata = resolve(rawMetadata, annotation.config());
            cache.put(method, metadata);
        }

        return metadata;
    }

    private OperationMetadata resolve(RawOperationMetadata metadata, String configName) {
        if (configName == null || configName.isBlank()) {
            return manager.merge(metadata);
        } else {
            return manager.merge(metadata, configName);
        }
    }

    private RawOperationMetadata buildMetadata(Idempotent annotation) {
        return DefaultRawOperationMetadata.builder()
                .headerName(annotation.headerName())
                .ttl(annotation.ttl())
                .timeUnit(annotation.timeUnit())
                .conflictHandleStrategy(annotation.onConflict())
                .fingerprintToggle(annotation.useFingerprint())
                .cacheToggle(annotation.shouldCache())
                .cache4xxToggle(annotation.shouldCache4xx())
                .cache5xxToggle(annotation.shouldCache5xx())
                .build();
    }
}
