package com.associago.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@TestPropertySource(properties = {
    "SUITE_ACCESS_ENABLED=true", "SUITE_ACCESS_OWNER_SUBJECT=user_owner00000001", "SUITE_ASSOCIATION_ID=1",
    "associago.clerk.issuer-uri=https://synthetic.example.invalid", "associago.cors.allowed-origins=https://synthetic.example.invalid",
    "spring.datasource.url=jdbc:h2:mem:suite-access-isolated;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SuiteAccessSecurityTest {
    static final String OWNER = "user_owner00000001", USER = "user_person0000001";
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ClerkDirectoryClient directory;
    @Autowired MockMvc mvc;
    @Autowired SuiteAccessUserRepository users;
    @Autowired SuiteAccessAuditRepository audit;
    @Autowired SuitePermissionCatalog catalog;
    @Autowired ObjectMapper mapper;
    @Autowired com.associago.user.repository.UserRepository localUsers;
    @BeforeEach void cleanIsolatedDatabase() { audit.deleteAll(); users.deleteAll(); }
    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("role", "presidente")
                .claim("o", Map.of("rol", "admin", "id", "org_unrelated")));
    }
    private SuiteAccessUser assigned(String status, String permissions) throws Exception {
        var user = new SuiteAccessUser(USER);
        var applications = mapper.readTree("{\"associago\":{\"role\":\"custom\",\"permissions\":" + permissions + "}}");
        user.grant(status, mapper.writeValueAsString(catalog.normalize(applications)));
        return users.saveAndFlush(user);
    }
    private String update(long version, String status, String apps) {
        return "{\"version\":" + version + ",\"status\":\"" + status + "\",\"applications\":" + apps + "}";
    }
    @Test void anonymousCannotReadDirectoryOrRegister() throws Exception {
        mvc.perform(get("/api/access/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/access/v1/users")).andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }
    @Test void organizationAdminAndLegacyRoleGrantNoApplicationAccess() throws Exception {
        mvc.perform(get("/api/access/v1/me").with(actor(USER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending")).andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.permissions").isEmpty()).andExpect(jsonPath("$.applications.associago.enabled").value(false));
        mvc.perform(get("/api/v1/associations").with(actor(USER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/access/v1/users").with(actor(USER))).andExpect(status().isForbidden());
    }
    @Test void ownerIsPinnedAndProtectedFromEveryUserUpdate() throws Exception {
        mvc.perform(get("/api/access/v1/me").with(actor(OWNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(true)).andExpect(jsonPath("$.canManageAccess").value(true))
                .andExpect(jsonPath("$.applications.bandi.enabled").value(true));
        mvc.perform(put("/api/access/v1/users/" + OWNER).with(actor(OWNER)).contentType("application/json")
                .content(update(0, "suspended", "{}"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OWNER_PROTECTED"));
        assertThat(audit.count()).isZero();
    }
    @Test void customReadDoesNotPermitWriteOrOtherModules() throws Exception {
        assigned("active", "[\"associago.activities.read\"]");
        mvc.perform(post("/api/v1/activities").with(actor(USER)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").with(actor(USER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/finance/transactions").with(actor(USER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/access/v1/catalog").with(actor(USER))).andExpect(status().isForbidden());
    }
    @Test void revokeIsEffectiveWithTheIdenticalOldJwtAndRetainsConfiguredGrants() throws Exception {
        var row = assigned("active", "[\"associago.members.read\"]");
        var oldJwt = actor(USER);
        mvc.perform(get("/api/v1/associations").with(oldJwt)).andExpect(status().isOk());
        String apps = "{\"associago\":{\"role\":\"custom\",\"permissions\":[\"associago.members.read\"]}}";
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json")
                .content(update(row.getVersion(), "suspended", apps))).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.configuredApplications.associago.permissions[0]").value("associago.members.read"));
        mvc.perform(get("/api/v1/associations").with(oldJwt)).andExpect(status().isForbidden());
        assertThat(audit.count()).isEqualTo(1);
    }
    @Test void optimisticConflictNeverOverwritesOtherAdministrations() throws Exception {
        var row = assigned("pending", "[]");
        String apps = "{\"bandi\":{\"role\":\"viewer\",\"permissions\":[]}}";
        var body = update(row.getVersion(), "active", apps);
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applications.bandi.enabled").value(true));
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCESS_VERSION_CONFLICT"));
        assertThat(audit.count()).isEqualTo(1);
    }
    @Test void managerOfAllApplicationsStillCannotManageAccessOrUseUnmappedAdministration() throws Exception {
        var row = new SuiteAccessUser(USER);
        row.grant("active", mapper.writeValueAsString(catalog.normalize(mapper.readTree("{\"associago\":{\"role\":\"manager\"}}"))));
        users.saveAndFlush(row);
        mvc.perform(get("/api/access/v1/users").with(actor(USER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/backup/create").with(actor(USER))).andExpect(status().isForbidden());
    }
    @Test void writeIncludesReadAndUnknownOrOwnerOnlyPermissionIsRejected() throws Exception {
        var row = assigned("pending", "[]");
        String apps = "{\"associago\":{\"role\":\"custom\",\"permissions\":[\"associago.activities.write\"]}}";
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json")
                .content(update(row.getVersion(), "active", apps))).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("associago.activities.read"))
                .andExpect(jsonPath("$.permissions[1]").value("associago.activities.write"));
        String invalid = "{\"bandi\":{\"role\":\"custom\",\"permissions\":[\"bandi.local.write\"]}}";
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json")
                .content(update(1, "active", invalid))).andExpect(status().isBadRequest());
        assertThat(audit.count()).isEqualTo(1);
    }
    @Test void pendingConfiguredPermissionsAreNeverEffective() throws Exception {
        assigned("pending", "[\"associago.members.read\"]");
        mvc.perform(get("/api/access/v1/me").with(actor(USER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty()).andExpect(jsonPath("$.applications.associago.enabled").value(false));
    }
    @Test void malformedRoleOrApplicationCannotBecomeOwner() throws Exception {
        var row = assigned("pending", "[]");
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json")
                .content(update(row.getVersion(), "active", "{\"associago\":{\"role\":\"owner\"}}")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/access/v1/users/" + USER).with(actor(OWNER)).contentType("application/json")
                .content(update(row.getVersion(), "active", "{\"arbitrary\":{\"role\":\"manager\"}}")))
                .andExpect(status().isBadRequest());
        assertThat(audit.count()).isZero();
    }
    @Test void evenOwnerCannotReopenDesktopAuthenticationOrBootstrapOnTheWeb() throws Exception {
        mvc.perform(post("/api/auth/login").with(actor(OWNER)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/associations/setup").with(actor(OWNER)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void directoryResponsesAreNeverCacheableOrVisibleToUnapprovedUsers() throws Exception {
        mvc.perform(get("/api/access/v1/users").with(actor(OWNER))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/access/v1/users/sync").with(actor(USER)).contentType("application/json").content("{\"offset\":0}"))
                .andExpect(status().isForbidden());
    }
    @Test void clerkSyncUpdatesDirectoryProfileWithoutChangingTheSavedGrants() throws Exception {
        assigned("active", "[\"associago.activities.read\"]");
        org.mockito.Mockito.when(directory.users(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ClerkDirectoryClient.Batch(java.util.List.of(new ClerkDirectoryClient.Profile(
                        USER, "Test Directory", "Test", "Directory", "directory@example.invalid")), true, null));
        mvc.perform(post("/api/access/v1/users/sync").with(actor(OWNER)).contentType("application/json").content("{\"offset\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.synced").value(1));
        mvc.perform(get("/api/access/v1/me").with(actor(USER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test Directory"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.permissions[0]").value("associago.activities.read"));
        assertThat(audit.count()).isZero();
    }
    @Test void desktopUserHeaderCannotChooseAWebIdentity() throws Exception {
        assigned("active", "[\"associago.members.read\"]");
        var other = new com.associago.user.User();
        other.setFirstName("Other"); other.setLastName("Person"); other.setClerkUserId("user_other00000001");
        other = localUsers.saveAndFlush(other);
        mvc.perform(get("/api/v1/me").with(actor(USER)).header("X-User-Id", other.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(other.getId().intValue())));
    }
}
