package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnSchemaModeCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        SchemaMode annotationMode = getAnnotationMode(metadata);
        SchemaMode envMode = getEnvMode(context);
        if (annotationMode.equals(envMode)) {
            return ConditionOutcome.match();
        }
        return ConditionOutcome.noMatch("Schema mode from environment not match with annotation");
    }

    private SchemaMode getAnnotationMode(AnnotatedTypeMetadata metadata) {
        String annotationName = ConditionalOnSchemaMode.class.getName();
        return (SchemaMode) AnnotationAttributeUtils.getAttribute(annotationName, "mode", metadata);
    }

    private SchemaMode getEnvMode(ConditionContext context) {
        String rawMode = context.getEnvironment().getProperty("idempify.schema.mode");
        if (StringUtils.isBlank(rawMode)) {
            return SchemaMode.fromStr(IdempifyDefaults.SCHEMA_MODE_VALUE);
        }
        return SchemaMode.fromStr(rawMode);
    }
}
