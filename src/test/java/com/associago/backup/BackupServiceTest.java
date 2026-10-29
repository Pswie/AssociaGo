package com.associago.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupServiceTest {

    @TempDir
    Path directory;

    @Test
    void snapshotUsesActiveSetupDatabaseAndIncludesCommittedWalPages() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + directory.resolve("setup.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl);
        try (Connection writer = dataSource.getConnection(); Statement statement = writer.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA wal_autocheckpoint=0");
            statement.execute("CREATE TABLE sample (name TEXT)");
            statement.execute("INSERT INTO sample VALUES ('latest committed row')");

            BackupService service = new BackupService(dataSource, directory.toString());
            Path backup = Path.of(service.createBackup());
            assertThat(backup).startsWith(directory.resolve("backups"));
            assertThat(Files.exists(directory.resolve("setup.db-wal"))).isTrue();
            try (Connection restored = DriverManager.getConnection("jdbc:sqlite:" + backup);
                 Statement query = restored.createStatement();
                 var rows = query.executeQuery("SELECT name FROM sample")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("latest committed row");
            }
            assertThat(service.createBackup()).isNotEqualTo(backup.toString());
        }
    }

    @Test
    void unsupportedDatabaseDoesNotProduceMisleadingLocalBackup() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:backup-test");
        var service = new BackupService(dataSource, directory.toString());
        assertThatThrownBy(service::createBackup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database remoto");
        assertThat(Files.exists(directory.resolve("backups"))).isFalse();
    }
}
