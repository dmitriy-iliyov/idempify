package io.github.dmitriyiliyov.idempify.core.config;

/**
 * Brings the storage schema up to what the library expects.
 * <p>
 * The context always holds exactly one: a migration module declares its own ahead of the core, and the core
 * falls back to {@link NoopIdempifySchemaMigrator}, which leaves the schema to the application. The module that
 * declares a migrator is also the one that runs it, as the init method of its bean - nothing else calls
 * {@link #migrate()}.
 */
public interface IdempifySchemaMigrator {
    /**
     * @throws IdempifyMigrationException when the schema could not be migrated
     */
    void migrate();
}
