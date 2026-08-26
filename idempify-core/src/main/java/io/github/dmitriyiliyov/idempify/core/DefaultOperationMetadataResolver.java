package io.github.dmitriyiliyov.idempify.core;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Objects;

public class DefaultOperationMetadataResolver implements OperationMetadataResolver {

    private final OperationMetadataCache cache;
    private final OperationMetadataManager manager;
    private final Object lock = new Object();

    public DefaultOperationMetadataResolver(OperationMetadataCache cache, OperationMetadataManager manager) {
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    @Override
    public OperationMetadata resolve(Method method, Class<?> targetClass) {
        Objects.requireNonNull(method, "method cannot be null");

        Method key = mostSpecificMethod(method, targetClass);

        OperationMetadata metadata = cache.get(key);
        if (metadata != null) {
            return metadata;
        }

        Idempotent annotation = findAnnotation(key);
        validateParams(annotation);
        RawOperationMetadata rawMetadata = buildRawMetadata(annotation);

        synchronized (lock) {
            metadata = cache.get(key);

            if (metadata != null) {
                return metadata;
            }

            metadata = resolveMetadata(rawMetadata, annotation.config());
            cache.put(key, metadata);
        }

        return metadata;
    }

    private Method mostSpecificMethod(Method method, Class<?> targetClass) {
        Class<?> userClass = targetClass == null ? null : ClassUtils.getUserClass(targetClass);
        return BridgeMethodResolver.findBridgedMethod(ClassUtils.getMostSpecificMethod(method, userClass));
    }

    private Idempotent findAnnotation(Method method) {
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (annotation == null) {
            throw new IllegalStateException("Method %s is not annotated with @Idempotent".formatted(method));
        }
        return annotation;
    }

    private void validateParams(Idempotent annotation) {
        if (!StringUtils.isBlank(annotation.idempotencyKey()) && !StringUtils.isBlank(annotation.headerName())) {
            throw new IllegalStateException(
                    """
                        idempotencyKey and headerName name two different sources of the key and cannot be combined,
                        but idempotencyKey=%s and headerName=%s were both given
                    """.formatted(annotation.idempotencyKey(), annotation.headerName())
            );
        }
    }

    private RawOperationMetadata buildRawMetadata(Idempotent annotation) {
        DefaultRawOperationMetadata.Builder builder = DefaultRawOperationMetadata.builder();

        if (!StringUtils.isBlank(annotation.idempotencyKey())) {
            builder.useHeaderName(false);
        } else {
            builder.useHeaderName(true)
                    .headerName(annotation.headerName());
        }

        return builder.ttl(annotation.ttl())
                .timeUnit(annotation.timeUnit())
                .processorType(annotation.processorType())
                .conflictHandleStrategy(annotation.onConflict())
                .fingerprintToggle(annotation.useFingerprint())
                .cacheToggle(annotation.useCache())
                .cache4xxToggle(annotation.cache4xx())
                .cache5xxToggle(annotation.cache5xx())
                .build();
    }

    private OperationMetadata resolveMetadata(RawOperationMetadata metadata, String configName) {
        if (configName == null || configName.isBlank()) {
            return manager.merge(metadata);
        } else {
            return manager.merge(metadata, configName);
        }
    }
}
