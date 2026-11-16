package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.config.SchemaMode;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

public final class SchemaProperties {

    private final SchemaMode mode;

    public SchemaProperties(@DefaultValue(IdempifyDefaults.SCHEMA_MODE_VALUE) SchemaMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode cannot be null");
    }

    public SchemaMode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "SchemaProperties{" +
                "mode=" + mode +
                '}';
    }
}
