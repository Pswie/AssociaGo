package com.associago.cloud;

import com.associago.attachment.Attachment;
import com.associago.attachment.AttachmentOwner;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Collaudo del profilo {@code cloud} contro un MySQL vero.
 *
 * <p>Verifica le due cose che nessuno aveva mai provato: che
 * {@code db/migration_mysql/V1__baseline_schema.sql} venga applicato da Flyway
 * su MySQL 8, e che le entity JPA combacino con lo schema che ne risulta —
 * quest'ultima grazie a {@code ddl-auto=validate}, che in esercizio resta
 * spento perché lo schema è di proprietà di Flyway.
 *
 * <p>Il {@link JwtDecoder} è sostituito da un mock: senza, il contesto
 * proverebbe a raggiungere il JWKS di Clerk in rete solo per collaudare il
 * database.
 */
@SpringBootTest
@ActiveProfiles("cloud")
@EnabledIf("databaseAvailable")
@DisplayName("Profilo cloud: baseline MySQL e mappatura delle entity")
class CloudMySqlSchemaIT {

    @Autowired
    jakarta.persistence.EntityManager attendanceEntityManager;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void attendanceMembershipReferencesRoundTripAndLegacyDuplicatesRemainValid() {
        AttendanceMappingProbe.verify(attendanceEntityManager);
    }

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("associago")
            .withUsername("associago")
            .withPassword("associago");

    /** Falso quando non c'è né un MySQL esterno né un Docker utilizzabile. */
    private static final boolean DATABASE_AVAILABLE;

    static {
        boolean available = true;
        if (System.getenv("MYSQL_TEST_URL") != null) {
            LocalJdbcTestTarget.requireLoopback(System.getenv("MYSQL_TEST_URL"), "mysql");
        } else {
            // Ciclo di vita gestito a mano invece che con @Container: il
            // container va avviato solo se non puntiamo a un database esterno.
            try {
                MYSQL.start();
            } catch (RuntimeException | LinkageError e) {
                // Tipico di Docker Desktop su Windows, dove Testcontainers non
                // individua il daemon. Il test si salta, non fallisce: e' un
                // limite dell'ambiente, non un difetto del codice.
                available = false;
            }
        }
        DATABASE_AVAILABLE = available;
    }

    @SuppressWarnings("unused")
    static boolean databaseAvailable() {
        return DATABASE_AVAILABLE;
    }

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    DataSource dataSource;

    @Autowired
    EntityManager entityManager;

    /**
     * Via di fuga per gli ambienti dove Testcontainers non individua il daemon
     * Docker (tipicamente Docker Desktop su Windows). Se {@code MYSQL_TEST_URL}
     * è valorizzata, il test usa quel database invece di avviare un container.
     */
    private static boolean useExternalDatabase() {
        return System.getenv("MYSQL_TEST_URL") != null;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (useExternalDatabase()) {
            registry.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("MYSQL_TEST_USER", "root"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
        } else {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }

        // Hibernate normalmente non tocca lo schema. Qui lo mettiamo a validare:
        // e' l'unico modo per accorgersi se una entity e la tabella hanno smesso
        // di combaciare.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access", () -> "true");

        // Valori fittizi ma non vuoti: CloudSecurityConfig rifiuta di partire se
        // mancano, ed e' esattamente il comportamento che vogliamo mantenere.
        registry.add("associago.clerk.issuer-uri", () -> "https://esempio.clerk.accounts.dev");
        registry.add("associago.cors.allowed-origins", () -> "https://esempio.org");
    }

    @Test
    @DisplayName("il baseline crea le tabelle attese e Flyway ne registra la versione")
    void baselineIsApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE()")) {
                assertThat(rs.next()).isTrue();
                // 58 tabelle di dominio piu' flyway_schema_history.
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(58);
            }

            try (ResultSet rs = statement.executeQuery(
                    "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("success")).isTrue();
            }
        }
    }

    @Test
    @Transactional
    void postgresCompatibleBinaryMappingStillRoundTripsLargeMysqlBlob() throws Exception {
        byte[] bytes = new byte[131_072];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i % 251);
        Attachment attachment = new Attachment();
        attachment.setOwnerType(AttachmentOwner.VOLUNTEER_EXPENSE);
        attachment.setOwnerId(999_002L);
        attachment.setKind("mysql-roundtrip");
        attachment.setFileName("roundtrip.bin");
        attachment.setContent(bytes);
        attachment.setSizeBytes(bytes.length);
        attachment.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        entityManager.persist(attachment);
        entityManager.flush();
        Long id = attachment.getId();
        entityManager.clear();
        assertThat(entityManager.find(Attachment.class, id).getContent()).containsExactly(bytes);
    }

    @Test
    @DisplayName("le tabelle che reggono la migrazione dalla dashboard Python esistono")
    void tablesNeededByTheMigrationExist() throws Exception {
        String[] expected = {
            "users", "user_associations", "members", "transactions",
            "volunteers", "volunteer_shifts", "volunteer_expenses",
            "association_deadlines", "associations"
        };

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : expected) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '" + table + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("tabella %s", table).isEqualTo(1);
                }
            }
        }
    }
}
