package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.StringUtils;

/**
 * Where a storage module keeps its migration scripts, declared by the module itself so that a migrator does
 * not have to guess the database from a connection.
 *
 * @param vendor   name of the database the scripts are written for
 * @param location resource location of the scripts, such as {@code classpath:db/idempify/postgresql}
 */
public record IdempifySchemaLocation(String vendor, String location) {

    public IdempifySchemaLocation {
        if (StringUtils.isBlank(vendor)) {
            throw new IllegalArgumentException("vendor cannot be null, empty or blank");
        }
        if (StringUtils.isBlank(location)) {
            throw new IllegalArgumentException("location cannot be null, empty or blank");
        }
        vendor = vendor.strip();
        location = location.strip();
    }
}
