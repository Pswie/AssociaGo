package com.associago.config;

import com.associago.backup.BackupService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlywayUpgradeBackupTests {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"32", "40"})
    void upgradePreservesRemovedFederationAndCouponDataInConsistentBackup(String version) throws Exception {
        DataSource dataSource = databaseAtVersion(version);
        seedHistoricalData(dataSource);
        BackupService backupService = new BackupService(dataSource, directory.toString());

        Flyway upgraded = new FlywayConfig().flyway(dataSource, backupService);
        upgraded.migrate();

        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("42");
        Path backup;
        try (var backups = Files.list(directory.resolve("backups"))) {
            var files = backups.toList();
            assertThat(files).hasSize(1);
            backup = files.getFirst();
        }
        DataSource snapshot = new DriverManagerDataSource("jdbc:sqlite:" + backup);
        assertThat(query(snapshot, "SELECT federation_registration_number FROM associations WHERE id = 1"))
                .isEqualTo("historical-registration");
        assertThat(query(snapshot, "SELECT COUNT(*) FROM coupon_activities")).isEqualTo("1");
        assertThat(query(snapshot, "SELECT discount_value FROM coupons WHERE id = 1")).isEqualTo("15");
        assertThat(query(snapshot, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo(version);

        assertThatThrownBy(() -> query(dataSource, "SELECT federation_registration_number FROM associations"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> query(dataSource, "SELECT COUNT(*) FROM coupon_activities"))
                .isInstanceOf(SQLException.class);
        assertThat(query(dataSource, "SELECT name FROM associations WHERE id = 1")).isEqualTo("Existing association");
    }

    @Test
    void failedBackupStopsUpgradeBeforeHistoricalDataIsRemoved() throws Exception {
        DataSource dataSource = databaseAtVersion("40");
        seedHistoricalData(dataSource);
        BackupService backupService = mock(BackupService.class);
        when(backupService.createBackup()).thenThrow(new IOException("Backup directory unavailable"));

        assertThatThrownBy(() -> new FlywayConfig().flyway(dataSource, backupService))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Aggiornamento interrotto");

        assertThat(query(dataSource, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("40");
        assertThat(query(dataSource, "SELECT federation_registration_number FROM associations WHERE id = 1"))
                .isEqualTo("historical-registration");
        assertThat(query(dataSource, "SELECT COUNT(*) FROM coupon_activities")).isEqualTo("1");
    }

    @Test
    void freshDatabaseDoesNotNeedPreUpgradeBackup() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("fresh.db"));
        BackupService backupService = mock(BackupService.class);

        new FlywayConfig().flyway(dataSource, backupService);

        verifyNoInteractions(backupService);
    }

    @Test
    void alreadyMigratedDatabaseDoesNotCreateAnotherBackupOnStartup() {
        DataSource dataSource = databaseAtVersion("41");
        BackupService backupService = mock(BackupService.class);

        new FlywayConfig().flyway(dataSource, backupService);

        verifyNoInteractions(backupService);
    }

    private DataSource databaseAtVersion(String version) {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("existing.db"));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
        return dataSource;
    }

    private void seedHistoricalData(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO associations (id, name, federation_registration_number) "
                    + "VALUES (1, 'Existing association', 'historical-registration')");
            statement.execute("INSERT INTO activities (id, association_id, name) VALUES (1, 1, 'Historical activity')");
            statement.execute("INSERT INTO coupons (id, association_id, code, discount_type, discount_value) "
                    + "VALUES (1, 1, 'OLD', 'PERCENTAGE', 15)");
            statement.execute("INSERT INTO coupon_activities (coupon_id, activity_id) VALUES (1, 1)");
        }
    }

    private String query(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
