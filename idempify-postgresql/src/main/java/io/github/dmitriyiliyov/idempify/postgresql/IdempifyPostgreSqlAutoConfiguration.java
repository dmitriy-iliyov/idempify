package io.github.dmitriyiliyov.idempify.postgresql;

import io.github.dmitriyiliyov.idempify.core.TransactionalOperationRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

@AutoConfiguration
@ConditionalOnProperty(
        prefix = "idempify",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class IdempifyPostgreSqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionalOperationRepository idempifyPostgreSqlOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlTransactionalOperationRepository(jdbcClient);
    }
}
