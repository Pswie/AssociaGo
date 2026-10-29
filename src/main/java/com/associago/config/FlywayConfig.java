package com.associago.config;

import com.associago.backup.BackupService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Flyway per la modalità desktop (migrazioni SQLite in classpath:db/migration).
 * Nel profilo "cloud" è disattivata: usa l'autoconfigurazione Flyway di Spring Boot,
 * che legge spring.flyway.locations (migrazioni MySQL).
 */
@Configuration
@Profile("!cloud")
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);
    private static final MigrationVersion DESTRUCTIVE_UPGRADE = MigrationVersion.fromVersion("41");

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource, BackupService backupService) {
        // Always run migrations to ensure schema consistency,
        // whether in setup mode (setup.db) or configured mode.
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        backupBeforeDestructiveUpgrade(dataSource, flyway, backupService);
        return flyway;
    }

    private void backupBeforeDestructiveUpgrade(DataSource dataSource, Flyway flyway, BackupService backupService) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:sqlite:")) {
                return;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Impossibile verificare il database prima della migrazione.", e);
        }

        var migrations = flyway.info();
        var current = migrations.current();
        if (current == null || current.getVersion() == null
                || current.getVersion().compareTo(DESTRUCTIVE_UPGRADE) >= 0) {
            return;
        }
        boolean destructiveUpgradePending = Arrays.stream(migrations.pending())
                .anyMatch(migration -> migration.getVersion() != null
                        && migration.getVersion().compareTo(DESTRUCTIVE_UPGRADE) >= 0);
        if (!destructiveUpgradePending) {
            return;
        }
        try {
            String backup = backupService.createBackup();
            logger.info("Backup SQLite prima dell'aggiornamento dalla versione {}: {}", current.getVersion(), backup);
        } catch (IOException e) {
            throw new IllegalStateException("Aggiornamento interrotto: impossibile creare il backup "
                    + "prima della migrazione che rimuove dati storici. Il database non è stato aggiornato.", e);
        }
    }
}
