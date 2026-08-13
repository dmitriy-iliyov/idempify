package io.github.dmitriyiliyov.idempify.postgresql;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

@AutoConfiguration
public class IdempifyPostgreSqlAutoConfiguration {

    @Bean
    public PostgreSqlTransactionalOperationRepository idempifyPostgreSqlOperationRepository(JdbcClient jdbcClient) {
        return new PostgreSqlTransactionalOperationRepository(jdbcClient);
    }
}
