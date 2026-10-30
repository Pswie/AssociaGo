import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.flywaydb.core.Flyway;

/** One-shot Flyway only: no Spring context, HTTP listener, identity or business bootstrap. */
public final class OwnerMigration {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected private properties and SQL directory");
        Properties input = new Properties();
        try (var reader = new InputStreamReader(Files.newInputStream(Path.of(args[0])), StandardCharsets.UTF_8)) {
            input.load(reader);
        }
        if (input.getProperty("trustStorePassword") != null) {
            // The store contains only the pinned provider CA; this affects this JVM only.
            System.setProperty("javax.net.ssl.trustStorePassword", input.getProperty("trustStorePassword"));
        }
        String schema = input.getProperty("schema");
        if (!"associago".equals(schema) && !"librepm".equals(schema)) {
            throw new IllegalArgumentException("Unexpected application schema");
        }
        var result = Flyway.configure()
                .dataSource(input.getProperty("url"), input.getProperty("user"), input.getProperty("password"))
                .defaultSchema(schema).schemas(schema).createSchemas(false)
                .locations("filesystem:" + Path.of(args[1]).toAbsolutePath())
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .load().migrate();
        System.out.println("OWNER_MIGRATION_OK:" + result.migrationsExecuted);
    }
}
