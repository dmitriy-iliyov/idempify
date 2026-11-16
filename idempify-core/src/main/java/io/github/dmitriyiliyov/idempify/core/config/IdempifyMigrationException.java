package io.github.dmitriyiliyov.idempify.core.config;

import java.util.Objects;

public class IdempifyMigrationException extends RuntimeException {

    private final String vendor;

    public IdempifyMigrationException(String vendor, Throwable cause) {
        super(
                "Migration of idempify failed on database '%s'".formatted(vendor),
                Objects.requireNonNull(cause, "cause cannot be null")
        );
        this.vendor = vendor;
    }

    public String getVendor() {
        return vendor;
    }
}
