package io.github.dmitriyiliyov.idempify.flyway;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyMigrationException;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaLocation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The scripts migrated here are the real ones the postgresql module ships, so a script Flyway cannot apply
 * fails here and not on the first start of an application.
 */
@Testcontainers
class FlywayIdempifySchemaMigratorIntegrationTest {

    private static final IdempifySchemaLocation POSTGRESQL_SCRIPTS =
            new IdempifySchemaLocation("postgresql", "classpath:db/idempify/postgresql");
    private static final String APPLICATION_HISTORY_TABLE = "flyway_schema_history";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbcClient;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DROP SCHEMA public CASCADE").update();
        jdbcClient.sql("CREATE SCHEMA public").update();
    }

    @Test
    @DisplayName("IT constructor() when dataSource is null should throw NullPointerException")
    void constructor_whenDataSourceIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new FlywayIdempifySchemaMigrator(null, POSTGRESQL_SCRIPTS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataSource cannot be null");
    }

    @Test
    @DisplayName("IT constructor() when schemaLocation is null should throw NullPointerException")
    void constructor_whenSchemaLocationIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new FlywayIdempifySchemaMigrator(dataSource, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("schemaLocation cannot be null");
    }

    @Test
    @DisplayName("IT migrate() when the schema is empty should create the operation table and record it in its own history")
    void migrate_whenSchemaIsEmpty_shouldCreateOperationTableAndRecordItInItsOwnHistory() {
        // given
        FlywayIdempifySchemaMigrator tested = new FlywayIdempifySchemaMigrator(dataSource, POSTGRESQL_SCRIPTS);

        // when
        tested.migrate();

        // then
        assertThat(tableExists("idempotent_operations")).isTrue();
        assertThat(appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE)).contains("1");
        assertThat(tableExists(APPLICATION_HISTORY_TABLE)).isFalse();
    }

    @Test
    @DisplayName("IT migrate() when the application has migrated its own tables should apply every script beside them")
    void migrate_whenApplicationHasMigratedItsOwnTables_shouldApplyEveryScriptBesideThem() {
        // given
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/application")
                .load()
                .migrate();
        FlywayIdempifySchemaMigrator tested = new FlywayIdempifySchemaMigrator(dataSource, POSTGRESQL_SCRIPTS);

        // when
        tested.migrate();

        // then
        assertThat(tableExists("idempotent_operations")).isTrue();
        assertThat(tableExists("orders")).isTrue();
        assertThat(appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE)).containsExactly("0", "1");
        assertThat(appliedScripts(APPLICATION_HISTORY_TABLE))
                .describedAs("the application's history must not see the library's versions")
                .containsExactly("V1__create_orders.sql");
    }

    @Test
    @DisplayName("IT migrate() when the schema is already migrated should apply nothing twice")
    void migrate_whenSchemaIsAlreadyMigrated_shouldApplyNothingTwice() {
        // given
        FlywayIdempifySchemaMigrator tested = new FlywayIdempifySchemaMigrator(dataSource, POSTGRESQL_SCRIPTS);
        tested.migrate();
        List<String> before = appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE);

        // when
        tested.migrate();

        // then
        assertThat(appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE)).isEqualTo(before);
    }

    @Test
    @DisplayName("IT migrate() when the application created the operation table itself should take it over")
    void migrate_whenApplicationCreatedOperationTableItself_shouldTakeItOver() {
        // given
        new FlywayIdempifySchemaMigrator(dataSource, POSTGRESQL_SCRIPTS).migrate();
        jdbcClient.sql("DROP TABLE " + FlywayIdempifySchemaMigrator.HISTORY_TABLE).update();
        FlywayIdempifySchemaMigrator tested = new FlywayIdempifySchemaMigrator(dataSource, POSTGRESQL_SCRIPTS);

        // when
        tested.migrate();

        // then
        assertThat(tableExists("idempotent_operations")).isTrue();
        assertThat(appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE)).containsExactly("0", "1");
    }

    @Test
    @DisplayName("IT migrate() when a script fails should name the database and keep what Flyway failed on")
    void migrate_whenScriptFails_shouldNameDatabaseAndKeepWhatFlywayFailedOn() {
        // given
        FlywayIdempifySchemaMigrator tested = new FlywayIdempifySchemaMigrator(
                dataSource,
                new IdempifySchemaLocation("postgresql", "classpath:db/idempify/broken")
        );

        // when / then
        assertThatThrownBy(tested::migrate)
                .isInstanceOfSatisfying(IdempifyMigrationException.class, ime -> {
                    assertThat(ime.getVendor()).isEqualTo("postgresql");
                    assertThat(ime.getCause()).isNotNull();
                });
    }

    private static boolean tableExists(String table) {
        return jdbcClient.sql("SELECT to_regclass(?) IS NOT NULL")
                .param("public." + table)
                .query(Boolean.class)
                .single();
    }

    private static List<String> appliedVersions(String historyTable) {
        return jdbcClient.sql("SELECT version FROM %s WHERE success ORDER BY installed_rank".formatted(historyTable))
                .query(String.class)
                .list();
    }

    private static List<String> appliedScripts(String historyTable) {
        return jdbcClient.sql("SELECT script FROM %s WHERE success ORDER BY installed_rank".formatted(historyTable))
                .query(String.class)
                .list();
    }
}
