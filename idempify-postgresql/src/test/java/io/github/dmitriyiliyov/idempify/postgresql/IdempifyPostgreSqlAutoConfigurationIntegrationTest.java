package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyPostgreSqlAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyPostgreSqlAutoConfiguration.class))
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class));

    @Test
    @DisplayName("IT context when a JdbcClient is present should register PostgreSqlTransactionalOperationRepository")
    void context_whenJdbcClientIsPresent_shouldRegisterRepository() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PostgreSqlTransactionalOperationRepository.class);
            assertThat(context).hasSingleBean(TransactionalOperationRepository.class);
            assertThat(context).hasSingleBean(OperationRepository.class);
        });
    }

    @Test
    @DisplayName("IT context when the application brings a store of its own should not register the postgres one")
    void context_whenApplicationBringsStoreOfItsOwn_shouldNotRegisterPostgresOne() {
        contextRunner
                .withBean("applicationOperationRepository", TransactionalOperationRepository.class,
                        () -> mock(TransactionalOperationRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TransactionalOperationRepository.class);
                    assertThat(context).doesNotHaveBean(PostgreSqlTransactionalOperationRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when no JdbcClient is present should fail to start")
    void context_whenNoJdbcClientIsPresent_shouldFailToStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdempifyPostgreSqlAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().hasMessageContaining(JdbcClient.class.getName());
                });
    }

    @Test
    @DisplayName("IT context when the PostgreSQL driver is missing should still register the repository")
    void context_whenPostgreSqlDriverIsMissing_shouldStillRegisterRepository() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Driver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PostgreSqlTransactionalOperationRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TransactionalOperationRepository.class);
                    assertThat(context).doesNotHaveBean(PostgreSqlTransactionalOperationRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PostgreSqlTransactionalOperationRepository.class));
    }
}
