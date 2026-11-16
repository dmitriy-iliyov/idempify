package io.github.dmitriyiliyov.idempify.flyway;

import io.github.dmitriyiliyov.idempify.core.config.IdempifyMigrationException;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaLocation;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaMigrator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migrator runs as the init method of its bean, so a context that registers it has already migrated: the
 * database behind these contexts is a real Postgres, emptied before each case.
 */
@Testcontainers
class IdempifyFlywayAutoConfigurationIntegrationTest {

    private static final IdempifySchemaLocation POSTGRESQL_SCRIPTS =
            new IdempifySchemaLocation("postgresql", "classpath:db/idempify/postgresql");
    private static final String APPLICATION_HISTORY_TABLE = "flyway_schema_history";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbcClient;

    private final ApplicationContextRunner contextRunner = contextRunnerWithoutSchemaLocation()
            .withBean(IdempifySchemaLocation.class, () -> POSTGRESQL_SCRIPTS);

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
    @DisplayName("IT autoConfiguration should be registered so that an application only adds the dependency")
    void autoConfiguration_shouldBeRegisteredSoThatApplicationOnlyAddsDependency() {
        // when
        Iterable<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        // then
        assertThat(candidates).contains(IdempifyFlywayAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("IT context when the schema mode is FLYWAY should register the Flyway migrator and migrate on start")
    void context_whenSchemaModeIsFlyway_shouldRegisterFlywayMigratorAndMigrateOnStart() {
        contextRunner
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempifySchemaMigrator.class);
                    assertThat(context).hasSingleBean(FlywayIdempifySchemaMigrator.class);
                    assertThat(operationTableExists()).isTrue();
                });
    }

    @Test
    @DisplayName("IT context when the schema mode is NONE should leave the schema alone")
    void context_whenSchemaModeIsNone_shouldLeaveSchemaAlone() {
        contextRunner
                .withPropertyValues("idempify.schema.mode=NONE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempifySchemaMigrator.class);
                    assertThat(operationTableExists()).isFalse();
                });
    }

    @Test
    @DisplayName("IT context when nothing is said about the schema should leave it alone though the module is present")
    void context_whenNothingIsSaidAboutSchema_shouldLeaveItAloneThoughModuleIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdempifySchemaMigrator.class);
            assertThat(operationTableExists()).isFalse();
        });
    }

    @Test
    @DisplayName("IT context when Flyway is not on the classpath should not register the migrator")
    void context_whenFlywayIsNotOnClasspath_shouldNotRegisterMigrator() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Flyway.class))
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempifySchemaMigrator.class);
                });
    }

    @Test
    @DisplayName("IT context when the library is switched off should not migrate anything")
    void context_whenLibraryIsSwitchedOff_shouldNotMigrateAnything() {
        contextRunner
                .withPropertyValues("idempify.enabled=false", "idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IdempifySchemaMigrator.class);
                    assertThat(operationTableExists()).isFalse();
                });
    }

    @Test
    @DisplayName("IT context when existing IdempifySchemaMigrator bean should not register the Flyway one")
    void context_whenExistingIdempifySchemaMigratorBean_shouldNotRegisterFlywayOne() {
        IdempifySchemaMigrator own = () -> { };

        contextRunner
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .withBean(IdempifySchemaMigrator.class, () -> own)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempifySchemaMigrator.class);
                    assertThat(context).doesNotHaveBean(FlywayIdempifySchemaMigrator.class);
                    assertThat(operationTableExists()).isFalse();
                });
    }

    @Test
    @DisplayName("IT context when a bean reads the store through JdbcTemplate should find the schema already migrated")
    void context_whenBeanReadsStoreThroughJdbcTemplate_shouldFindSchemaAlreadyMigrated() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
                .withUserConfiguration(StoreReadingConfiguration.class)
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context)
                            .describedAs("a bean on JdbcTemplate is created after every database initializer")
                            .hasNotFailed();
                    assertThat(context.getBean(StoreReader.class).operations()).isZero();
                });
    }

    @Test
    @DisplayName("IT context when the application runs its own Flyway should migrate after it, each in its own history")
    void context_whenApplicationRunsItsOwnFlyway_shouldMigrateAfterItEachInItsOwnHistory() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withPropertyValues("idempify.schema.mode=FLYWAY", "spring.flyway.locations=classpath:db/application")
                .run(context -> {
                    assertThat(context)
                            .describedAs("the application's Flyway refuses a non-empty schema it has no history of")
                            .hasNotFailed();
                    assertThat(appliedScripts(APPLICATION_HISTORY_TABLE)).containsExactly("V1__create_orders.sql");
                    assertThat(appliedVersions(FlywayIdempifySchemaMigrator.HISTORY_TABLE))
                            .describedAs("a baseline is taken only on a schema the application has already filled")
                            .containsExactly("0", "1");
                });
    }

    @Test
    @DisplayName("IT context when a script fails should refuse to start naming the database")
    void context_whenScriptFails_shouldRefuseToStartNamingDatabase() {
        contextRunnerWithoutSchemaLocation()
                .withBean(IdempifySchemaLocation.class,
                        () -> new IdempifySchemaLocation("postgresql", "classpath:db/idempify/broken"))
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(IdempifyMigrationException.class.getName())
                            .hasStackTraceContaining("Migration of idempify failed on database 'postgresql'");
                });
    }

    @Test
    @Disabled("M11")
    @DisplayName("IT context when no storage module declares where its scripts are should refuse naming the way out")
    void context_whenNoStorageModuleDeclaresWhereItsScriptsAre_shouldRefuseNamingWayOut() {
        contextRunnerWithoutSchemaLocation()
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .describedAs("a bare missing-bean failure does not say that a storage module is missing")
                            .isNotInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasMessageContaining("idempify.schema.mode is FLYWAY")
                            .hasMessageContaining("idempify.schema.mode=NONE");
                });
    }

    private static ApplicationContextRunner contextRunnerWithoutSchemaLocation() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyFlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource);
    }

    private static boolean operationTableExists() {
        return jdbcClient.sql("SELECT to_regclass('public.idempotent_operations') IS NOT NULL")
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

    @Configuration(proxyBeanMethods = false)
    static class StoreReadingConfiguration {

        @Bean
        StoreReader storeReader(JdbcTemplate jdbcTemplate) {
            return new StoreReader(jdbcTemplate);
        }
    }

    /**
     * Reads the operation table as soon as it is built, the way an application bean might on start.
     */
    static final class StoreReader {

        private final int operations;

        StoreReader(JdbcTemplate jdbcTemplate) {
            this.operations = jdbcTemplate.queryForObject("SELECT count(*) FROM idempotent_operations", Integer.class);
        }

        int operations() {
            return operations;
        }
    }
}
