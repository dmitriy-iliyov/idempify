package io.github.dmitriyiliyov.idempify.flyway;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyMigrationException;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaLocation;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaMigrator;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Applies the scripts of the storage module with a Flyway of its own, apart from the one the application may
 * run: the history is kept in {@value #HISTORY_TABLE}, so the two never see each other's versions.
 * <p>
 * The schema is baselined at version 0 because it is not empty by the time the library arrives - the
 * application's own tables are already there - and every script of the library then still applies.
 */
public class FlywayIdempifySchemaMigrator implements IdempifySchemaMigrator {

    static final String HISTORY_TABLE = "idempify_schema_history";
    private final DataSource dataSource;
    private final IdempifySchemaLocation schemaLocation;

    public FlywayIdempifySchemaMigrator(DataSource dataSource, IdempifySchemaLocation schemaLocation) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.schemaLocation = Objects.requireNonNull(schemaLocation, "schemaLocation cannot be null");
    }

    @Override
    public void migrate() {
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(schemaLocation.location())
                    .table(HISTORY_TABLE)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        } catch (Exception e) {
            throw new IdempifyMigrationException(schemaLocation.vendor(), e);
        }
    }
}
