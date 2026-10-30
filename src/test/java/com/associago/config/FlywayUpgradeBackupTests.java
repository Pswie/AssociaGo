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

        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("45");
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
        DataSource dataSource = databaseAtVersion("45");
        BackupService backupService = mock(BackupService.class);

        new FlywayConfig().flyway(dataSource, backupService);

        verifyNoInteractions(backupService);
    }

    private DataSource databaseAtVersion(String version) {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("existing.db"));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
        return dataSource;
    }

    @Test
    void attendanceUpgradeBacksUpAndPreservesLegacyDuplicatesIdsNotesSequenceAndReferences() throws Exception {
        DataSource dataSource = databaseAtVersion("44");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO associations (id,name) VALUES (1,'Fixture')");
            statement.execute("INSERT INTO activities (id,association_id,name) VALUES (1,1,'Attendance fixture')");
            statement.execute("INSERT INTO members (id,first_name,last_name) VALUES (301,'Legacy','Fixture')");
            statement.execute("INSERT INTO users (id,first_name,last_name) VALUES (7,'Membership','Fixture')");
            statement.execute("INSERT INTO user_associations (id,user_id,association_id) VALUES (301,7,1)");
            statement.execute("INSERT INTO attendance_sessions (id,activity_id,date) VALUES (9,1,'2026-10-30')");
            statement.execute("INSERT INTO attendance_records (id,session_id,member_id,status,notes) VALUES (11,9,301,'PRESENT','historical note'),(12,9,301,'LATE','duplicate retained'),(99,9,301,'ABSENT','deleted high watermark')");
            statement.execute("DELETE FROM attendance_records WHERE id=99");
        }
        BackupService backup = new BackupService(dataSource, directory.toString());
        Flyway upgraded = new FlywayConfig().flyway(dataSource, backup);
        upgraded.migrate();
        Path archive;
        try (var files = Files.list(directory.resolve("backups"))) { archive = files.findFirst().orElseThrow(); }
        DataSource snapshot = new DriverManagerDataSource("jdbc:sqlite:" + archive);
        String rows = "SELECT group_concat(id || ':' || member_id || ':' || status || ':' || notes, '|') FROM (SELECT * FROM attendance_records ORDER BY id)";
        assertThat(query(dataSource, rows)).isEqualTo(query(snapshot, rows));
        assertThat(query(dataSource, "SELECT COUNT(*) FROM attendance_records WHERE membership_id IS NOT NULL")).isEqualTo("0");
        assertThat(query(snapshot, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("44");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("INSERT INTO attendance_records (session_id,membership_id,status,notes) VALUES (9,301,'PRESENT','new explicit membership')");
            assertThat(query(dataSource, "SELECT MAX(id) FROM attendance_records")).isEqualTo("100");
            assertThatThrownBy(() -> statement.execute("INSERT INTO attendance_records(session_id,membership_id) VALUES(9,301)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO attendance_records(session_id,membership_id) VALUES(9,404)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("DELETE FROM user_associations WHERE id=301"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(query(dataSource, "SELECT COUNT(*) FROM attendance_records WHERE member_id=301")).isEqualTo("2");
        assertThat(query(dataSource, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='ux_attendance_session_membership'")).isEqualTo("1");
    }

    @Test
    void attendanceRebuildIsBlockedWhenItsConsistentBackupCannotBeCreated() throws Exception {
        DataSource dataSource = databaseAtVersion("44");
        BackupService backup = mock(BackupService.class);
        when(backup.createBackup()).thenThrow(new IOException("Unavailable"));
        assertThatThrownBy(() -> new FlywayConfig().flyway(dataSource, backup))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Aggiornamento interrotto");
        assertThat(query(dataSource, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")).isEqualTo("44");
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
