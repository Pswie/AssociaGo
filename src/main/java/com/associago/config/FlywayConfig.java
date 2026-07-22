package com.associago.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Flyway per la modalità desktop (migrazioni SQLite in classpath:db/migration).
 * Nel profilo "cloud" è disattivata: usa l'autoconfigurazione Flyway di Spring Boot,
 * che legge spring.flyway.locations (migrazioni MySQL).
 */
@Configuration
@Profile("!cloud")
public class FlywayConfig {

    private final AppConfigManager configManager;

    public FlywayConfig(AppConfigManager configManager) {
        this.configManager = configManager;
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        // Always run migrations to ensure schema consistency, 
        // whether in setup mode (setup.db) or configured mode.
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
