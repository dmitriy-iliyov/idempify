package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.StringUtils;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public final class AnnotationAttributeUtils {

    private AnnotationAttributeUtils() { }

    /**
     * Reads the same attribute as {@link #getAttribute}, but for an annotation that may not be there at all:
     * an absent annotation, an absent attribute and one carrying no value all answer {@code null}.
     */
    public static Object findAttribute(String annotationName, String attributeName, AnnotatedTypeMetadata metadata) {
        if (StringUtils.isBlank(attributeName)) {
            throw new IllegalArgumentException("attributeName cannot be null, empty or blank");
        }

        Map<String, Object> attributes = metadata.getAnnotationAttributes(annotationName);
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        return attributes.get(attributeName);
    }

    public static Object getAttribute(String annotationName, String attributeName, AnnotatedTypeMetadata metadata) {
        if (StringUtils.isBlank(attributeName)) {
            throw new IllegalArgumentException("attributeName cannot be null, empty or blank");
        }

        Map<String, Object> attributes = metadata.getAnnotationAttributes(annotationName);
        if (attributes == null) {
            throw new IllegalStateException("Attributes for %s annotation is null".formatted(annotationName));
        }

        if (attributes.isEmpty()) {
            throw new IllegalStateException("Attributes for %s annotation is empty".formatted(annotationName));
        }

        Object rawProcessorType = attributes.get(attributeName);
        if (rawProcessorType == null) {
            throw new IllegalStateException("Attribute '%s' for %s annotation is null".formatted(attributeName, annotationName));
        }

        return rawProcessorType;
    }
}
