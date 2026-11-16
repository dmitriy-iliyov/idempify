package io.github.dmitriyiliyov.idempify.core.config;

import java.util.Arrays;

/**
 * Who brings the storage schema up. The value of {@code idempify.schema.mode} is matched against the
 * {@link ConditionalOnSchemaMode} of every migration module, so a module on the classpath migrates nothing
 * until it is chosen.
 * <p>
 * {@code NONE} leaves the schema to the application; {@code FLYWAY} hands it to {@code idempify-flyway}.
 */
public enum SchemaMode {
    NONE, DEV, FLYWAY;

    public static SchemaMode fromStr(String str) {
        return Arrays.stream(SchemaMode.values())
                .filter(m -> m.name().equalsIgnoreCase(str))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'idempify.schema.mode' must be one of %s, but was %s"
                                .formatted(Arrays.toString(values()), str)
                ));
    }
}
