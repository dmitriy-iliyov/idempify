package io.github.dmitriyiliyov.idempify.postgresql;

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
    public PostgreSqlTransactionalOperationRepository idempifyPostgreSqlOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlTransactionalOperationRepository(jdbcClient);
    }
}
