package com.associago.cloud;

import com.associago.association.Association;
import com.associago.attachment.Attachment;
import com.associago.attachment.AttachmentOwner;
import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import com.associago.member.Member;
import com.associago.project.Project;
import com.associago.project.ProjectAssignment;
import com.associago.security.CloudSecurityConfig;
import com.associago.suite.SuiteProjectLink;
import com.associago.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real PostgreSQL baseline, JDBC binary mapping and runtime-role verification. */
@SpringBootTest
@ActiveProfiles("cloud-postgres")
@EnabledIf("databaseAvailable")
class CloudPostgresSchemaIT {
    private static final boolean EXTERNAL = System.getenv("POSTGRES_TEST_URL") != null;
    private static final String CONTAINER_OWNER = "associago_test_owner";
    private static final String CONTAINER_APP = "associago_test_app";
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String APP_PASSWORD = UUID.randomUUID().toString();

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("associago_test").withUsername("associago_test_admin")
            .withPassword(UUID.randomUUID().toString());
    private static final boolean AVAILABLE;

    static {
        boolean available = true;
        if (EXTERNAL) {
            LocalJdbcTestTarget.requireLoopback(System.getenv("POSTGRES_TEST_URL"), "postgresql");
        } else {
            try {
                POSTGRES.start();
            } catch (RuntimeException | LinkageError unavailable) {
                available = false;
            }
            if (available) provisionContainerRoles();
        }
        AVAILABLE = available;
    }

    static boolean databaseAvailable() {
        return AVAILABLE;
    }

    private static void provisionContainerRoles() {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + CONTAINER_OWNER + " LOGIN PASSWORD '" + OWNER_PASSWORD
                    + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS");
            statement.execute("CREATE ROLE " + CONTAINER_APP + " LOGIN PASSWORD '" + APP_PASSWORD
                    + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS");
            statement.execute("CREATE SCHEMA associago AUTHORIZATION " + CONTAINER_OWNER);
            statement.execute("GRANT USAGE ON SCHEMA associago TO " + CONTAINER_APP);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + CONTAINER_OWNER
                    + " IN SCHEMA associago GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + CONTAINER_APP);
            statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + CONTAINER_OWNER
                    + " IN SCHEMA associago GRANT USAGE, SELECT ON SEQUENCES TO " + CONTAINER_APP);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot prepare dedicated PostgreSQL test roles.", failure);
        }
    }

    private static String runtimeUser() {
        return EXTERNAL ? System.getenv().getOrDefault("POSTGRES_TEST_USER", "postgres") : CONTAINER_APP;
    }

    private static String migrationUser() {
        return EXTERNAL ? System.getenv().getOrDefault("POSTGRES_TEST_MIGRATION_USER", runtimeUser()) : CONTAINER_OWNER;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EXTERNAL ? System.getenv("POSTGRES_TEST_URL") : POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.username", CloudPostgresSchemaIT::runtimeUser);
        registry.add("spring.datasource.password", () -> EXTERNAL
                ? System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "") : APP_PASSWORD);
        // Supabase can use a separate DDL role, keeping CREATE away from the runtime role.
        registry.add("spring.flyway.url", () -> EXTERNAL ? System.getenv("POSTGRES_TEST_URL") : POSTGRES.getJdbcUrl());
        registry.add("spring.flyway.user", CloudPostgresSchemaIT::migrationUser);
        registry.add("spring.flyway.password", () -> EXTERNAL ? System.getenv().getOrDefault("POSTGRES_TEST_MIGRATION_PASSWORD",
                System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "")) : OWNER_PASSWORD);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        // The base YAML also sets the JPA-standard action to none, which otherwise
        // takes precedence over hbm2ddl.auto and silently disables validation.
        registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", () -> "validate");
        registry.add("associago.clerk.issuer-uri", () -> "https://example.clerk.accounts.dev");
        registry.add("associago.cors.allowed-origins", () -> "https://example.org");
    }

    @MockitoBean
    JwtDecoder jwtDecoder;
    @Autowired
    DataSource dataSource;
    @Autowired
    Flyway flyway;
    @Autowired
    Environment environment;
    @Autowired
    ApplicationContext context;
    @Autowired
    EntityManager entityManager;

    @Test
    void optInProfileKeepsCloudSecurityAndCreatesOnlyPrivateApplicationTables() throws Exception {
        assertThat(environment.acceptsProfiles(Profiles.of("cloud"))).isTrue();
        assertThat(context.getBeansOfType(CloudSecurityConfig.class)).hasSize(1);
        assertThat(environment.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("associago");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(flyway.info().pending()).isEmpty();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema='associago' AND table_type='BASE TABLE'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(64);
            }
            try (var rows = statement.executeQuery("SELECT to_regclass('public.associations'), "
                    + "to_regclass('associago.suite_project_links'), to_regclass('associago.project_person_assignments')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
                assertThat(rows.getString(2)).isNotNull();
                assertThat(rows.getString(3)).isNotNull();
            }
        }
    }

    @Test
    @Transactional
    void binaryAttachmentRoundTripsAsByteaWithoutLargeObjectOid() throws Exception {
        byte[] bytes = new byte[131_072];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i % 251);
        Attachment attachment = new Attachment();
        attachment.setOwnerType(AttachmentOwner.VOLUNTEER_EXPENSE);
        attachment.setOwnerId(999_001L);
        attachment.setKind("postgres-roundtrip");
        attachment.setFileName("roundtrip.bin");
        attachment.setMimeType("application/octet-stream");
        attachment.setContent(bytes);
        attachment.setSizeBytes(bytes.length);
        attachment.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        entityManager.persist(attachment);
        entityManager.flush();
        Long id = attachment.getId();
        entityManager.clear();

        assertThat(entityManager.find(Attachment.class, id).getContent()).containsExactly(bytes);
        assertThat(entityManager.createNativeQuery("SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema='associago' AND table_name='attachments' AND column_name='content'", String.class)
                .getSingleResult()).isEqualTo("bytea");
        assertThat(entityManager.createNativeQuery("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema='associago' AND udt_name='oid'", Long.class).getSingleResult()).isEqualTo(0L);
    }

    @Test
    @Transactional
    void clerkNumericIdentityProjectsSuiteLinksAndMoneyKeepTheirMappings() {
        Association association = new Association();
        association.setName("PostgreSQL mapping test");
        association.setSlug("postgres-mapping-test");
        association.setEmail("association@example.org");
        entityManager.persist(association);
        User user = new User();
        user.setFirstName("PostgreSQL");
        user.setLastName("Test");
        user.setClerkUserId("user_postgres_mapping_test");
        entityManager.persist(user);
        Project project = new Project();
        project.setAssociationId(association.getId());
        project.setName("A real project");
        project.setBudget(new BigDecimal("12345.67"));
        entityManager.persist(project);
        SuiteProjectLink link = new SuiteProjectLink();
        link.setSuiteProjectId("014c7052-44c6-4a1d-a278-a9bb13cafdc2");
        link.setOrganizationId("7310d217-dff9-4231-8381-87d2e4290fc3");
        link.setProjectId(project.getId());
        entityManager.persist(link);
        Member member = new Member();
        member.setFirstName("Assignment");
        member.setLastName("Test");
        entityManager.persist(member);
        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setProjectId(project.getId());
        assignment.setPersonType("MEMBER");
        assignment.setPersonId(member.getId());
        entityManager.persist(assignment);
        Transaction transaction = new Transaction();
        transaction.setAssociationId(association.getId());
        transaction.setProjectId(project.getId());
        transaction.setDate(LocalDate.of(2026, 10, 30));
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal("123.45"));
        transaction.setBaseAmount(new BigDecimal("123.45"));
        entityManager.persist(transaction);
        entityManager.flush();
        entityManager.clear();

        assertThat(user.getId()).isPositive();
        assertThat(entityManager.find(User.class, user.getId()).getClerkUserId()).isEqualTo("user_postgres_mapping_test");
        assertThat(entityManager.find(Project.class, project.getId()).getBudget()).isEqualByComparingTo("12345.67");
        assertThat(entityManager.find(SuiteProjectLink.class, link.getSuiteProjectId()).getProjectId()).isEqualTo(project.getId());
        assertThat(entityManager.find(Transaction.class, transaction.getId()).getAmount()).isEqualByComparingTo("123.45");
    }

    @Test
    void runtimeRoleCanUseDataWithoutOwningSchemaWhenSeparateRoleIsConfigured() throws Exception {
        assumeTrue(!runtimeUser().equals(migrationUser()),
                "Privilege verification requires separate runtime and migration database roles.");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT has_schema_privilege(current_user, 'associago', 'CREATE'), "
                     + "has_table_privilege(current_user, 'associago.attachments', 'INSERT'), "
                     + "has_table_privilege(current_user, 'associago.projects', 'SELECT')")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isFalse();
            assertThat(rows.getBoolean(2)).isTrue();
            assertThat(rows.getBoolean(3)).isTrue();
        }
    }

    @Test
    @Transactional
    void attendanceMembershipReferencesRoundTripAndLegacyDuplicatesRemainValid() {
        AttendanceMappingProbe.verify(entityManager);
    }

    @Test
    @org.junit.jupiter.api.Timeout(30)
    void simultaneousAttendanceWritesLockTheSessionAndCreateExactlyOneMembershipRecord() throws Exception {
        var transactions = new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        String subject = "user_attendance_" + UUID.randomUUID();
        long[] ids = transactions.execute(status -> {
            var association = new Association(); association.setName("Concurrent attendance fixture"); association.setSlug("concurrent-" + UUID.randomUUID()); association.setEmail("attendance-concurrent@example.invalid"); entityManager.persist(association);
            var user = new User(); user.setFirstName("Operator"); user.setLastName("Fixture"); user.setClerkUserId(subject); entityManager.persist(user);
            var membership = new com.associago.membership.UserAssociation(); membership.setUser(user); membership.setAssociation(association); entityManager.persist(membership);
            var activity = new com.associago.activity.Activity(); activity.setAssociationId(association.getId()); activity.setName("Concurrent fixture"); entityManager.persist(activity);
            var session = new com.associago.attendance.AttendanceSession(); session.setActivityId(activity.getId()); session.setDate(LocalDate.now()); entityManager.persist(session);
            return new long[]{association.getId(), user.getId(), membership.getId(), activity.getId(), session.getId()};
        });
        var actor = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "none").subject(subject).build(),
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PRESIDENTE")));
        var attendance = context.getBean(com.associago.attendance.AttendanceService.class);
        var start = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<String> write = () -> {
            start.await();
            try {
                attendance.createRecord(new com.associago.attendance.dto.AttendanceRecordUpsertDTO(ids[4], null, "PRESENT", null, null, ids[2]), actor, ids[0]);
                return "saved";
            } catch (com.associago.suite.SuiteException conflict) {
                if (conflict.getStatus() != 409) throw conflict;
                return "conflict";
            }
        };
        try {
            var first = workers.submit(write); var second = workers.submit(write); start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "conflict");
            Long count = transactions.execute(status -> entityManager.createQuery(
                    "select count(record) from AttendanceRecord record where record.sessionId = :id", Long.class).setParameter("id", ids[4]).getSingleResult());
            assertThat(count).isEqualTo(1L);
        } finally {
            workers.shutdownNow();
            transactions.executeWithoutResult(status -> {
                entityManager.createQuery("delete from AttendanceRecord record where record.sessionId=:id").setParameter("id", ids[4]).executeUpdate();
                entityManager.createQuery("delete from AttendanceSession session where session.id=:id").setParameter("id", ids[4]).executeUpdate();
                entityManager.createQuery("delete from Activity activity where activity.id=:id").setParameter("id", ids[3]).executeUpdate();
                entityManager.createQuery("delete from UserAssociation membership where membership.id=:id").setParameter("id", ids[2]).executeUpdate();
                entityManager.createQuery("delete from User user where user.id=:id").setParameter("id", ids[1]).executeUpdate();
                entityManager.createQuery("delete from Association association where association.id=:id").setParameter("id", ids[0]).executeUpdate();
            });
        }
    }

    @Test
    @Transactional
    void fiscalCodeUniquenessStillIgnoresCase() {
        User first = person("PGCODECASE");
        entityManager.persist(first);
        entityManager.flush();
        User duplicate = person("pgcodecase");

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @Transactional
    void sharedEmailsRemainAllowedAndOpaqueClerkIdsRemainExact() {
        User first = person("PGSHAREDONE");
        first.setEmail("shared@example.org");
        first.setClerkUserId("user_CaseSensitive");
        User second = person("PGSHAREDTWO");
        second.setEmail("SHARED@example.org");
        second.setClerkUserId("user_casesensitive");
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(User.class, first.getId()).getClerkUserId()).isEqualTo("user_CaseSensitive");
        assertThat(entityManager.find(User.class, second.getId()).getClerkUserId()).isEqualTo("user_casesensitive");
    }

    private User person(String taxCode) {
        User person = new User();
        person.setFirstName("PostgreSQL");
        person.setLastName("Identity");
        person.setTaxCode(taxCode);
        return person;
    }
}
