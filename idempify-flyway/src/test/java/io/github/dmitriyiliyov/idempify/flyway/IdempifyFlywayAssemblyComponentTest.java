package io.github.dmitriyiliyov.idempify.flyway;

import io.github.dmitriyiliyov.idempify.aop.IdempifyAopAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.OperationStateChannel;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyCoreAutoConfiguration;
import io.github.dmitriyiliyov.idempify.core.config.IdempifySchemaMigrator;
import io.github.dmitriyiliyov.idempify.core.config.NoopIdempifySchemaMigrator;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizerCreator;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyFormat;
import io.github.dmitriyiliyov.idempify.core.request.KeyExtractor;
import io.github.dmitriyiliyov.idempify.core.request.RequestContextProvider;
import io.github.dmitriyiliyov.idempify.core.request.RequestType;
import io.github.dmitriyiliyov.idempify.core.response.ResponseDeserializer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseSerializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.result.ResultSerializer;
import io.github.dmitriyiliyov.idempify.postgresql.IdempifyPostgreSqlAutoConfiguration;
import io.github.dmitriyiliyov.idempify.starter.IdempifyAutoConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The migration module as an application gets it: next to the starter, the core and the storage module whose
 * scripts it applies, over a real Postgres - so that which migrator the context ends up with, and whether the
 * start-up check accepts it, is decided by the real auto-configurations and their order, not by a stand-in.
 * <p>
 * Only the serialization and the transport, which take no part in the schema, answer as mocks.
 */
@Testcontainers
class IdempifyFlywayAssemblyComponentTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbcClient;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdempifyAutoConfiguration.class,
                    IdempifyCoreAutoConfiguration.class,
                    IdempifyAopAutoConfiguration.class,
                    IdempifyPostgreSqlAutoConfiguration.class,
                    IdempifyFlywayAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    JdbcClientAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    TransactionAutoConfiguration.class
            ))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(ResultSerializer.class, () -> mock(ResultSerializer.class))
            .withBean(ResultDeserializer.class, () -> mock(ResultDeserializer.class))
            .withBean(ResponseSerializer.class, () -> mock(ResponseSerializer.class))
            .withBean(ResponseDeserializer.class, () -> mock(ResponseDeserializer.class))
            .withBean(OperationStateChannel.class, () -> mock(OperationStateChannel.class))
            .withBean(RequestContextProvider.class, () -> mock(RequestContextProvider.class))
            .withBean(KeyExtractor.class, IdempifyFlywayAssemblyComponentTest::httpKeyExtractor)
            .withBean(BodyCanonicalizerCreator.class, IdempifyFlywayAssemblyComponentTest::jsonCanonicalizerCreator);

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
    @DisplayName("CT application when the schema mode is FLYWAY should start on the Flyway migrator with the table in place")
    void application_whenSchemaModeIsFlyway_shouldStartOnFlywayMigratorWithTableInPlace() {
        contextRunner
                .withPropertyValues("idempify.schema.mode=FLYWAY")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempifySchemaMigrator.class);
                    assertThat(context.getBean(IdempifySchemaMigrator.class))
                            .describedAs("the module declares its migrator ahead of the core's no-op one")
                            .isInstanceOf(FlywayIdempifySchemaMigrator.class);
                    assertThat(operationTableExists()).isTrue();
                });
    }

    @Test
    @DisplayName("CT application when nothing is said about the schema should start on the no-op migrator and leave it alone")
    void application_whenNothingIsSaidAboutSchema_shouldStartOnNoopMigratorAndLeaveItAlone() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IdempifySchemaMigrator.class);
            assertThat(context.getBean(IdempifySchemaMigrator.class)).isInstanceOf(NoopIdempifySchemaMigrator.class);
            assertThat(operationTableExists()).isFalse();
        });
    }

    private static boolean operationTableExists() {
        return jdbcClient.sql("SELECT to_regclass('public.idempotent_operations') IS NOT NULL")
                .query(Boolean.class)
                .single();
    }

    private static KeyExtractor httpKeyExtractor() {
        KeyExtractor extractor = mock(KeyExtractor.class);
        when(extractor.getRequestType()).thenReturn(RequestType.HTTP);
        return extractor;
    }

    private static BodyCanonicalizerCreator jsonCanonicalizerCreator() {
        BodyCanonicalizerCreator creator = mock(BodyCanonicalizerCreator.class);
        when(creator.getFormat()).thenReturn(BodyFormat.JSON);
        when(creator.create(any())).thenReturn(mock(BodyCanonicalizer.class));
        return creator;
    }
}
