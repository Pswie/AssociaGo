package com.associago.membership;

import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@TestPropertySource(properties = {
    "associago.clerk.issuer-uri=https://example.clerk.accounts.dev", "associago.cors.allowed-origins=https://example.org",
    "spring.datasource.url=jdbc:h2:mem:membership-boundary;DB_CLOSE_DELAY=-1;MODE=MySQL", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class MembershipBoundaryTests {
    @MockitoBean JwtDecoder decoder;
    @Autowired MockMvc http;
    @Autowired UserRepository users;
    @Autowired AssociationRepository associations;
    @Autowired UserAssociationRepository memberships;
    Association own, foreign;
    User manager, candidate;
    UserAssociation ownMembership, foreignMembership;
    String subject;
    @BeforeEach void seedTwoAssociations() {
        subject = "membership-fixture-" + UUID.randomUUID();
        own = association("Own"); foreign = association("Foreign");
        manager = person("Manager"); manager.setClerkUserId(subject); manager = users.saveAndFlush(manager);
        candidate = person("Candidate");
        ownMembership = membership(manager, own);
        foreignMembership = membership(person("Foreign"), foreign);
    }
    @Test void callerCannotCreateTheForeignActiveMembershipThatWouldAuthorizeItsOwnRequest() throws Exception {
        long count = memberships.count();
        http.perform(post("/api/v1/memberships").with(actor()).header("X-Association-Id", own.getId())
                .contentType("application/json").content(body(manager.getId(), foreign.getId())))
                .andExpect(status().isForbidden());
        http.perform(post("/api/v1/memberships").with(actor()).header("X-Association-Id", foreign.getId())
                .contentType("application/json").content(body(manager.getId(), foreign.getId())))
                .andExpect(status().isForbidden());
        assertThat(memberships.count()).isEqualTo(count);
        assertThat(memberships.findByUserIdAndAssociationId(manager.getId(), foreign.getId())).isEmpty();
    }
    @Test void foreignReadListUpdateDeleteAndForgedSelectedHeaderAreDenied() throws Exception {
        String path = "/api/v1/memberships/" + foreignMembership.getId();
        http.perform(get(path).with(actor())).andExpect(status().isForbidden());
        http.perform(get("/api/v1/memberships/association/" + foreign.getId()).with(actor())).andExpect(status().isForbidden());
        http.perform(put(path).with(actor()).contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isForbidden());
        http.perform(delete(path).with(actor())).andExpect(status().isForbidden());
        http.perform(get("/api/v1/memberships/" + ownMembership.getId()).with(actor()).header("X-Association-Id", foreign.getId())).andExpect(status().isForbidden());
        assertThat(memberships.findById(foreignMembership.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }
    @Test void createRejectsPrefilledIdAndUpdateCannotRebindUserOrAssociation() throws Exception {
        http.perform(post("/api/v1/memberships").with(actor()).contentType("application/json")
                        .content("{\"id\":" + foreignMembership.getId() + ",\"user\":{\"id\":" + candidate.getId() + "},\"association\":{\"id\":" + own.getId() + "}}"))
                .andExpect(status().isBadRequest());
        http.perform(put("/api/v1/memberships/" + ownMembership.getId()).with(actor()).contentType("application/json")
                        .content(body(candidate.getId(), own.getId())))
                .andExpect(status().isConflict());
        http.perform(put("/api/v1/memberships/" + ownMembership.getId()).with(actor()).contentType("application/json")
                        .content(body(manager.getId(), foreign.getId())))
                .andExpect(status().isConflict());
        var unchanged = memberships.findById(ownMembership.getId()).orElseThrow();
        assertThat(unchanged.getUser().getId()).isEqualTo(manager.getId()); assertThat(unchanged.getAssociation().getId()).isEqualTo(own.getId());
    }
    @Test void ownAssociationCreationResolvesOnlyIdsAndSafeResponseContainsNoAssociationCredentials() throws Exception {
        http.perform(post("/api/v1/memberships").with(actor()).header("X-Association-Id", own.getId()).contentType("application/json")
                        .content("{\"user\":{\"id\":" + candidate.getId() + ",\"firstName\":\"FORGED\",\"clerkUserId\":\"FORGED\"},\"association\":{\"id\":" + own.getId() + ",\"name\":\"FORGED\",\"password\":\"FORGED\"},\"status\":\"ACTIVE\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.firstName").value("Candidate"))
                .andExpect(jsonPath("$.association.name").value("Own"))
                .andExpect(jsonPath("$.association.password").doesNotExist()).andExpect(jsonPath("$.association.dbPassword").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andExpect(jsonPath("$.user.clerkUserId").doesNotExist());
        assertThat(users.findById(candidate.getId()).orElseThrow().getClerkUserId()).isNull();
        assertThat(associations.findById(own.getId()).orElseThrow().getPassword()).isEqualTo("PRIVATE_ASSOC_PASSWORD_FIXTURE");
        var saved = memberships.findByUserIdAndAssociationId(candidate.getId(), own.getId()).orElseThrow();
        http.perform(put("/api/v1/memberships/" + saved.getId()).with(actor()).contentType("application/json").content("{\"status\":\"SUSPENDED\",\"notes\":\"updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        http.perform(delete("/api/v1/memberships/" + saved.getId()).with(actor())).andExpect(status().isNoContent());
    }
    @Test void unlinkedIdentityCannotCreateAnyMembership() throws Exception {
        http.perform(post("/api/v1/memberships").with(jwt().jwt(token -> token.subject("unlinked-fixture")).authorities(new SimpleGrantedAuthority("ROLE_PRESIDENTE")))
                        .contentType("application/json").content(body(candidate.getId(), own.getId())))
                .andExpect(status().isForbidden());
        assertThat(memberships.findByUserIdAndAssociationId(candidate.getId(), own.getId())).isEmpty();
    }
    @Test void suspendedMembershipCannotAuthorizeCreateAndDuplicateActiveMembershipIsRejected() throws Exception {
        http.perform(post("/api/v1/memberships").with(actor()).contentType("application/json").content(body(manager.getId(), own.getId())))
                .andExpect(status().isConflict());
        ownMembership.setStatus("SUSPENDED"); memberships.saveAndFlush(ownMembership);
        http.perform(post("/api/v1/memberships").with(actor()).contentType("application/json").content(body(candidate.getId(), own.getId())))
                .andExpect(status().isForbidden());
        assertThat(memberships.findByUserIdAndAssociationId(candidate.getId(), own.getId())).isEmpty();
    }
    @Test void userPostCannotMergeUnlinkRelinkOrCreateAClerkIdentity() throws Exception {
        http.perform(post("/api/v1/users").with(actor()).contentType("application/json")
                        .content("{\"id\":" + manager.getId() + ",\"firstName\":\"FORGED\",\"lastName\":\"Fixture\",\"clerkUserId\":null}"))
                .andExpect(status().isBadRequest());
        for (String clerk : new String[]{subject, ""}) {
            http.perform(post("/api/v1/users").with(actor()).contentType("application/json")
                            .content("{\"firstName\":\"New\",\"lastName\":\"Fixture\",\"clerkUserId\":\"" + clerk + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.findById(manager.getId()).orElseThrow().getClerkUserId()).isEqualTo(subject);
        http.perform(post("/api/v1/users").with(actor()).contentType("application/json").content("{\"firstName\":\"Legitimate\",\"lastName\":\"Fixture\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.clerkUserId").isEmpty());
    }
    @Test void userPutPathIdentityAndExistingClerkLinkRemainImmutable() throws Exception {
        http.perform(put("/api/v1/users/" + manager.getId()).with(actor()).contentType("application/json")
                        .content("{\"id\":" + candidate.getId() + ",\"firstName\":\"FORGED\"}"))
                .andExpect(status().isConflict());
        http.perform(put("/api/v1/users/" + manager.getId()).with(actor()).contentType("application/json")
                        .content("{\"firstName\":\"FORGED\",\"clerkUserId\":\"other-fixture\"}"))
                .andExpect(status().isConflict());
        assertThat(users.findById(manager.getId()).orElseThrow().getClerkUserId()).isEqualTo(subject);
        assertThat(users.findById(manager.getId()).orElseThrow().getFirstName()).isEqualTo("Manager");
    }
    @Test void legitimateContactUpdateCannotClearAnExistingClerkLinkByOmission() throws Exception {
        http.perform(put("/api/v1/users/" + manager.getId()).with(actor()).contentType("application/json")
                        .content("{\"firstName\":\"Updated\",\"lastName\":\"Fixture\",\"email\":\"updated@example.invalid\",\"clerkUserId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Updated"));
        assertThat(users.findById(manager.getId()).orElseThrow().getClerkUserId()).isEqualTo(subject);
    }
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor() {
        return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_REFERENTE_VOLONTARI"));
    }
    private Association association(String name) {
        var row = new Association(); row.setName(name); row.setSlug(UUID.randomUUID().toString()); row.setEmail(UUID.randomUUID()+"@example.invalid");
        row.setPassword("PRIVATE_ASSOC_PASSWORD_FIXTURE"); row.setDbPassword("PRIVATE_DB_PASSWORD_FIXTURE"); return associations.saveAndFlush(row);
    }
    private User person(String name) { var row = new User(); row.setFirstName(name); row.setLastName("Fixture"); return users.saveAndFlush(row); }
    private UserAssociation membership(User user, Association association) {
        var row = new UserAssociation(); row.setUser(user); row.setAssociation(association); return memberships.saveAndFlush(row);
    }
    private String body(Long user, Long association) { return "{\"user\":{\"id\":"+user+"},\"association\":{\"id\":"+association+"},\"status\":\"ACTIVE\",\"role\":\"MEMBER\"}"; }
}
