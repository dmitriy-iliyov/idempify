package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.BlockingOperationRepository;
import io.github.dmitriyiliyov.idempify.core.response.RawResponseContainer;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempifyPostgreSqlAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempifyPostgreSqlAutoConfiguration.class))
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class));

    @Test
    @DisplayName("IT context when a JdbcClient is present should register both repositories")
    void context_whenJdbcClientIsPresent_shouldRegisterBothRepositories() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PostgreSqlBlockingOperationRepository.class);
            assertThat(context).hasSingleBean(BlockingOperationRepository.class);

            assertThat(context).hasSingleBean(PostgreSqlResponseRepository.class);
            assertThat(context).hasSingleBean(ResponseRepository.class);
        });
    }

    @Test
    @DisplayName("IT context when the application brings a store of its own should not register the postgres one")
    void context_whenApplicationBringsStoreOfItsOwn_shouldNotRegisterPostgresOne() {
        contextRunner
                .withBean("applicationBlockingOperationRepository", BlockingOperationRepository.class,
                        () -> mock(BlockingOperationRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(BlockingOperationRepository.class);
                    assertThat(context).doesNotHaveBean(PostgreSqlBlockingOperationRepository.class);
                });
    }


    @Test
    @DisplayName("IT context when the application brings a response store of its own should not register the postgres one")
    void context_whenApplicationBringsResponseStoreOfItsOwn_shouldNotRegisterPostgresOne() {
        contextRunner
                .withBean("applicationResponseRepository", ResponseRepository.class,
                        () -> mock(ResponseRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseRepository.class);
                    assertThat(context).doesNotHaveBean(PostgreSqlResponseRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when a wrapper is registered should hand out the response repository already wrapped")
    void context_whenWrapperIsRegistered_shouldHandOutResponseRepositoryAlreadyWrapped() {
        contextRunner
                .withBean(ResponseRepositoryWrapper.class, MarkingWrapper::new)
                .run(context -> assertThat(context.getBean(ResponseRepository.class))
                        .isInstanceOf(MarkedResponseRepository.class));
    }

    @Test
    @DisplayName("IT context when no wrapper is registered should hand out the postgres repository itself")
    void context_whenNoWrapperIsRegistered_shouldHandOutPostgresRepositoryItself() {
        contextRunner.run(context -> assertThat(context.getBean(ResponseRepository.class))
                .isExactlyInstanceOf(PostgreSqlResponseRepository.class));
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
                    assertThat(context).hasSingleBean(PostgreSqlBlockingOperationRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched off should register nothing at all")
    void context_whenIdempifyIsSwitchedOff_shouldRegisterNothingAtAll() {
        contextRunner
                .withPropertyValues("idempify.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BlockingOperationRepository.class);
                    assertThat(context).doesNotHaveBean(PostgreSqlBlockingOperationRepository.class);
                    assertThat(context).doesNotHaveBean(OperationRepository.class);
                    assertThat(context).doesNotHaveBean(ResponseRepository.class);
                });
    }

    @Test
    @DisplayName("IT context when idempify is switched on by hand should register the module all the same")
    void context_whenIdempifyIsSwitchedOnByHand_shouldRegisterModuleAllTheSame() {
        contextRunner
                .withPropertyValues("idempify.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PostgreSqlBlockingOperationRepository.class));
    }

    /**
     * Stands in for a cache: whatever it wraps comes back as a type the assertion can recognise, so a test
     * can tell a wrapped repository from the bare one.
     */
    private static final class MarkingWrapper implements ResponseRepositoryWrapper {

        @Override
        public ResponseRepository wrap(ResponseRepository repository) {
            return new MarkedResponseRepository();
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }

    private static final class MarkedResponseRepository implements ResponseRepository {

        @Override
        public RawResponseContainer save(UUID idempotencyKey, String response) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RawResponseContainer> findByIdempotencyKey(UUID idempotencyKey) {
            throw new UnsupportedOperationException();
        }
    }
}
