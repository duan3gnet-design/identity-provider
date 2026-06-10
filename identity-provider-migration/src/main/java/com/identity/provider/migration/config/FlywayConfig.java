package com.identity.provider.migration.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flywayAuth(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .schemas("public")
                .baselineOnMigrate(true)
                .load();
    }
}
