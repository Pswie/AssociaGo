package com.associago.suite;

import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.member.Member;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.project.Project;
import com.associago.project.repository.ProjectAssignmentRepository;
import com.associago.project.repository.ProjectRepository;
import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import com.associago.volunteer.Volunteer;
import com.associago.volunteer.repository.VolunteerRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "SUITE_ENABLED=true", "SUITE_ORGANIZATION_ID=11111111-1111-4111-8111-111111111111",
        "SUITE_ASSOCIATION_ID=1", "SUITE_ASSOCIAGO_KEY=test-only-associago-peer-key-1234567890"
})
@AutoConfigureMockMvc
class SuiteProjectIntegrationTests {
    private static final String ORG = "11111111-1111-4111-8111-111111111111";
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private static final String SECOND_ID = "33333333-3333-4333-8333-333333333333";
    private static final String KEY = "test-only-associago-peer-key-1234567890";
    private static final Path DATA = temporaryDirectory();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("associago.data.path", DATA::toString); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ProjectRepository projects;
    @Autowired SuiteProjectLinkRepository links;
    @Autowired AssociationRepository associations;
    @Autowired TransactionRepository transactions;
    @Autowired ProjectAssignmentRepository assignments;
    @Autowired VolunteerShiftRepository shifts;
    @Autowired UserAssociationRepository memberships;
    @Autowired UserRepository users;
    @Autowired VolunteerRepository volunteers;
    @Autowired MemberRepository members;

    @BeforeEach
    void clean() {
        shifts.deleteAll();
        assignments.deleteAll();
        links.deleteAll();
        transactions.deleteAll();
        projects.deleteAll();
        volunteers.deleteAll();
        memberships.deleteAll();
        users.deleteAll();
        members.deleteAll();
        if (associations.findById(1L).isEmpty()) association("suite-association");
    }

    @Test
    void peerImportIsIdempotentAndNeverGeneratesAccountingEntries() throws Exception {
        ObjectNode body = projection(1);
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value("associago")).andExpect(jsonPath("$.financial.expense").isString());
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(body))).andExpect(status().isOk());
        assertThat(projects.count()).isEqualTo(1);
        assertThat(links.count()).isEqualTo(1);
        assertThat(transactions.count()).isZero();
        assertThat(projects.findAll().get(0).getBudget()).isEqualByComparingTo("100.25");
    }

    @Test
    void rejectsMissingWrongPeerCredentialsAndForeignOrganizationBeforeWriting() throws Exception {
        byte[] body = mapper.writeValueAsBytes(projection(1));
        mvc.perform(put(peerPath(ID)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(peer(put(peerPath(ID))).header("X-Suite-Key", "wrong").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(peerPath(ID)).contentType(MediaType.APPLICATION_JSON).header("X-Suite-Peer", "librepm")
                .header("X-Suite-Key", KEY).header("X-Suite-Organization", SECOND_ID).content(body)).andExpect(status().isForbidden());
        ObjectNode foreign = projection(1).put("organizationId", SECOND_ID);
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(foreign))).andExpect(status().isForbidden());
        assertThat(projects.count()).isZero();
    }

    @Test
    void protectsVersionOrderingAndPayloadIdentity() throws Exception {
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(2)))).andExpect(status().isOk());
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(1)))).andExpect(status().isConflict());
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(2).put("title", "Different"))))
                .andExpect(status().isConflict());
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(3).put("title", "Updated"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceVersion").value(3));
        assertThat(projects.findAll().get(0).getName()).isEqualTo("Updated");
    }

    @Test
    void rejectsOverflowAndNonRepresentableCurrencyOrPrecision() throws Exception {
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(1).put("sourceVersion", 2147483648L))))
                .andExpect(status().isBadRequest());
        ObjectNode currency = projection(1);
        ((ObjectNode) currency.get("plannedBudget")).put("currency", "USD");
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(currency))).andExpect(status().isConflict());
        ObjectNode precision = projection(1);
        ((ObjectNode) precision.get("plannedBudget")).put("amount", "0.001");
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(precision))).andExpect(status().isConflict());
        assertThat(projects.count()).isZero();
    }

    @Test
    void linksOnlySameAssociationAndDoesNotRebindOrOverwriteLocalNotes() throws Exception {
        Project local = project(1L, "Existing");
        local.setNotes("Local operational note");
        projects.saveAndFlush(local);
        ObjectNode body = projection(1).put("targetLocalId", local.getId().toString());
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(body))).andExpect(status().isOk());
        assertThat(projects.findById(local.getId()).orElseThrow().getNotes()).isEqualTo("Local operational note");
        ObjectNode rebind = projection(1).put("suiteProjectId", SECOND_ID).put("targetLocalId", local.getId().toString());
        mvc.perform(peer(put(peerPath(SECOND_ID))).content(mapper.writeValueAsBytes(rebind))).andExpect(status().isConflict());
        Association other = association("foreign-association");
        Project foreign = project(other.getId(), "Foreign");
        rebind.put("targetLocalId", foreign.getId().toString());
        mvc.perform(peer(put(peerPath(SECOND_ID))).content(mapper.writeValueAsBytes(rebind))).andExpect(status().isForbidden());
        assertThat(projects.count()).isEqualTo(2);
    }

    @Test
    void snapshotUsesOnlyRealSameAssociationTransactionsAndExportsNoPeople() throws Exception {
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(1)))).andExpect(status().isOk());
        long projectId = projects.findAll().get(0).getId();
        transaction(1L, projectId, "500.00", TransactionType.INCOME);
        transaction(1L, projectId, "120.50", TransactionType.EXPENSE);
        Association other = association("foreign-transactions");
        transaction(other.getId(), projectId, "999.00", TransactionType.EXPENSE);
        String response = mvc.perform(peer(get(peerPath(ID)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = mapper.readTree(response);
        assertThat(new BigDecimal(json.path("financial").path("income").asText())).isEqualByComparingTo("500.00");
        assertThat(new BigDecimal(json.path("financial").path("expense").asText())).isEqualByComparingTo("120.50");
        assertThat(new BigDecimal(json.path("financial").path("balance").asText())).isEqualByComparingTo("379.50");
        assertThat(json.has("people")).isFalse();
        assertThat(json.path("preparation").isNull()).isTrue();
    }

    @Test
    void linkingAccountingEntryIsRepeatableAndDoesNotDuplicateTheEntry() throws Exception {
        Project project = project(1L, "Accounting");
        Transaction transaction = transaction(1L, null, "20.00", TransactionType.EXPENSE);
        mvc.perform(post("/api/v1/projects/{id}/transactions/{tx}", project.getId(), transaction.getId())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/{id}/transactions/{tx}", project.getId(), transaction.getId())).andExpect(status().isOk());
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(transactions.findById(transaction.getId()).orElseThrow().getProjectId()).isEqualTo(project.getId());
        Project second = project(1L, "Other project");
        mvc.perform(post("/api/v1/projects/{id}/transactions/{tx}", second.getId(), transaction.getId())).andExpect(status().isConflict());
    }

    @Test
    void assignmentAndCompletedHoursAreRealAndSourceIsAssociationScoped() throws Exception {
        mvc.perform(peer(put(peerPath(ID))).content(mapper.writeValueAsBytes(projection(1)))).andExpect(status().isOk());
        Project project = projects.findAll().get(0);
        User user = new User();
        user.setFirstName("Test"); user.setLastName("Member"); user.setTaxCode("RSSMRA80A01H501U"); user.setEmail("fixture@example.org");
        user = users.saveAndFlush(user);
        UserAssociation membership = new UserAssociation(); membership.setUser(user); membership.setAssociation(associations.findById(1L).orElseThrow());
        membership = memberships.saveAndFlush(membership);
        byte[] memberAssignment = mapper.writeValueAsBytes(Map.of("personType", "MEMBER", "personId", membership.getId()));
        mvc.perform(post("/api/v1/projects/{id}/assignments", project.getId()).contentType(MediaType.APPLICATION_JSON).content(memberAssignment)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/{id}/assignments", project.getId()).contentType(MediaType.APPLICATION_JSON).content(memberAssignment)).andExpect(status().isOk());
        assertThat(assignments.count()).isEqualTo(1);
        Member member = new Member(); member.setFirstName("Test"); member.setLastName("Member"); member.setFiscalCode(user.getTaxCode());
        member = members.saveAndFlush(member);
        Volunteer volunteer = new Volunteer(); volunteer.setMemberId(member.getId()); volunteer.setStatus("ACTIVE"); volunteer = volunteers.save(volunteer);
        ObjectNode shift = mapper.createObjectNode().put("volunteerId", volunteer.getId()).put("status", "COMPLETED")
                .put("startTime", "2026-10-29T10:00:00").put("endTime", "2026-10-29T11:30:00").put("hoursWorked", "1.5");
        mvc.perform(post("/api/v1/projects/{id}/shifts", project.getId()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(shift)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/projects/{id}/assignments", project.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(Map.of("personType", "VOLUNTEER", "personId", volunteer.getId())))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/{id}/shifts", project.getId()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(shift)))
                .andExpect(status().isOk());
        String response = mvc.perform(peer(get(peerPath(ID)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = mapper.readTree(response);
        assertThat(json.path("workforce").path("assignedCount").asInt()).isEqualTo(2);
        assertThat(new BigDecimal(json.path("workforce").path("completedHours").asText())).isEqualByComparingTo("1.5");
        assertThat(response).doesNotContain("fixture@example.org");
    }

    private ObjectNode projection(long version) {
        ObjectNode body = mapper.createObjectNode().put("schemaVersion", 1).put("organizationId", ORG).put("suiteProjectId", ID)
                .put("sourceVersion", version).put("title", "Shared test project").put("description", "Source description");
        body.putObject("plannedBudget").put("amount", "100.25").put("currency", "EUR");
        body.putNull("projectDeadline"); body.putNull("targetLocalId"); body.putObject("links");
        return body;
    }

    private MockHttpServletRequestBuilder peer(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).header("X-Suite-Peer", "librepm").header("X-Suite-Key", KEY).header("X-Suite-Organization", ORG);
    }
    private String peerPath(String id) { return "/api/integrations/v1/peer/projects/" + id; }
    private Association association(String slug) {
        Association value = new Association(); value.setName(slug); value.setSlug(slug); value.setType("APS"); value.setEmail(slug + "@example.org"); value.setPassword("fixture-only");
        return associations.saveAndFlush(value);
    }
    private Project project(long associationId, String name) {
        Project project = new Project(); project.setAssociationId(associationId); project.setName(name); return projects.saveAndFlush(project);
    }
    private Transaction transaction(long associationId, Long projectId, String amount, TransactionType type) {
        Transaction transaction = new Transaction(); transaction.setAssociationId(associationId); transaction.setProjectId(projectId);
        transaction.setAmount(new BigDecimal(amount)); transaction.setDate(LocalDate.of(2026, 10, 29)); transaction.setType(type);
        return transactions.saveAndFlush(transaction);
    }
    private static Path temporaryDirectory() {
        try { return Files.createTempDirectory("associago-suite-tests-"); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
}
