package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.ConditionalOnIdempifyEnabled;
import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

@AutoConfiguration
@ConditionalOnIdempifyEnabled
public class IdempifyPostgreSqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionalOperationRepository idempifyPostgreSqlOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlTransactionalOperationRepository(jdbcClient);
    }
}
