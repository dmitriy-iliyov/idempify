package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import io.github.dmitriyiliyov.idempify.core.config.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepository;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapper;
import io.github.dmitriyiliyov.idempify.core.response.ResponseRepositoryWrapperUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Set;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
public class IdempifyPostgreSqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperationRepository idempifyOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlOperationRepository(jdbcClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseRepository idempifyResponseRepository(JdbcClient jdbcClient,
                                                         Set<ResponseRepositoryWrapper> wrappers) {
        return ResponseRepositoryWrapperUtils.wrapWithPriority(
                new PostgreSqlResponseRepository(jdbcClient),
                wrappers
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionalOperationRepository idempifyTransactionalOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlTransactionalOperationRepository(jdbcClient);
    }
}
