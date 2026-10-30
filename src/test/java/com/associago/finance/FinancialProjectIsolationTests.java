package com.associago.finance;

import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.finance.repository.*;
import com.associago.project.Project;
import com.associago.project.ProjectService;
import com.associago.project.repository.ProjectRepository;
import com.associago.report.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class FinancialProjectIsolationTests {
    static final Path DATA=temp();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("associago.data.path",DATA::toString); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AssociationRepository associations;
    @Autowired ProjectRepository projects;
    @Autowired TransactionRepository transactions;
    @Autowired JournalEntryRepository journal;
    @Autowired FinancialService financial;
    @Autowired ProjectService projectService;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean ReportService reports;
    Association first,other;
    @BeforeEach void setup() {
        first=association(); other=association();
    }
    @Test void headerScopesTransactionsJournalAndComparisonsEvenWithoutDateFilters() throws Exception {
        transaction(first.getId(),null,"20.00",LocalDate.of(2026,1,1));
        transaction(other.getId(),null,"900.00",LocalDate.of(2026,1,1));
        journal(first.getId()); journal(other.getId());
        mvc.perform(get("/api/v1/finance/transactions").header("X-Association-Id",first.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].associationId").value(first.getId()));
        mvc.perform(get("/api/v1/finance/journal-entries").header("X-Association-Id",first.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/finance/journal-entries").header("X-Association-Id",first.getId()).param("startDate","2026-01-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/finance/yoy-comparison").header("X-Association-Id",first.getId()).param("year","2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incomeCurrent").value(20));
        mvc.perform(get("/api/v1/finance/comparison").header("X-Association-Id",first.getId()).param("year1","2026").param("year2","2025"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incomeYear1").value(20));
    }
    @Test void financialPdfIncludesOnlyTheSelectedAssociationAndYearAndReceiptChecksScope() throws Exception {
        Transaction current=transaction(first.getId(),null,"20",LocalDate.of(2026,1,1));
        transaction(first.getId(),null,"50",LocalDate.of(2025,1,1));
        Transaction foreign=transaction(other.getId(),null,"900",LocalDate.of(2026,1,1));
        when(reports.generateFinancialReport(anyInt(),anyMap(),anyList(),any())).thenReturn(new byte[]{1,2,3});
        mvc.perform(get("/api/v1/reports/finance/year/2026").header("X-Association-Id",first.getId())).andExpect(status().isOk());
        verify(reports).generateFinancialReport(eq(2026),anyMap(),argThat(list -> list.size()==1 && list.getFirst().getId().equals(current.getId())),any());
        mvc.perform(get("/api/v1/reports/finance/transactions/{id}/receipt",foreign.getId()).header("X-Association-Id",first.getId())).andExpect(status().isForbidden());
    }
    @Test void legacyUpdateCannotMoveAnAttributedEntryOrUseAnotherAssociation() throws Exception {
        Project one=project(first.getId()),two=project(first.getId());
        Transaction entry=transaction(first.getId(),one.getId(),"20",LocalDate.now());
        Map<String,Object> body=Map.of("associationId",first.getId(),"date",LocalDate.now().toString(),"amount","20.00","type","INCOME","description","Edited","projectId",two.getId());
        mvc.perform(put("/api/v1/finance/transactions/{id}",entry.getId()).header("X-Association-Id",first.getId()).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/finance/transactions/{id}",entry.getId()).header("X-Association-Id",other.getId())).andExpect(status().isForbidden());
        assertEquals(one.getId(),transactions.findById(entry.getId()).orElseThrow().getProjectId());
    }
    @Test void deletePreservesReferencedProjectsAndBalancesExcludeForeignLegacyRows() throws Exception {
        Project project=project(first.getId());
        Transaction own=transaction(first.getId(),project.getId(),"20",LocalDate.now());
        transaction(other.getId(),project.getId(),"900",LocalDate.now());
        assertEquals(0,projectService.balance(project.getId(),LocalDate.now()).orElseThrow().income().compareTo(new BigDecimal("20")));
        mvc.perform(delete("/api/v1/projects/{id}",project.getId())).andExpect(status().isConflict());
        assertTrue(projects.existsById(project.getId())); assertEquals(project.getId(),transactions.findById(own.getId()).orElseThrow().getProjectId());
        Project empty=project(first.getId()); mvc.perform(delete("/api/v1/projects/{id}",empty.getId())).andExpect(status().isNoContent());
        assertFalse(projects.existsById(empty.getId()));
    }
    @Test void competingAtomicAttachmentsChooseOneProjectAndDeletedTargetsCannotReceiveEntries() throws Exception {
        Project one=project(first.getId()),two=project(first.getId());
        Transaction entry=transaction(first.getId(),null,"20",LocalDate.now());
        List<Integer> outcomes=concurrent(
                () -> new TransactionTemplate(manager).execute(s -> transactions.attachIfAvailable(entry.getId(),first.getId(),one.getId(),LocalDateTime.now())),
                () -> new TransactionTemplate(manager).execute(s -> transactions.attachIfAvailable(entry.getId(),first.getId(),two.getId(),LocalDateTime.now())));
        assertEquals(1,outcomes.stream().mapToInt(Integer::intValue).sum());
        assertTrue(projects.existsById(transactions.findById(entry.getId()).orElseThrow().getProjectId()));
        Project removed=project(first.getId()); long removedId=removed.getId(); projectService.delete(removedId);
        Transaction unassigned=transaction(first.getId(),null,"1",LocalDate.now());
        int changed=new TransactionTemplate(manager).execute(s -> transactions.attachIfAvailable(unassigned.getId(),first.getId(),removedId,LocalDateTime.now()));
        assertEquals(0,changed); assertNull(transactions.findById(unassigned.getId()).orElseThrow().getProjectId());
    }
    @Test void attachAndDeleteRaceNeverLeavesAReferenceToAMissingProject() throws Exception {
        Project target=project(first.getId()); Transaction entry=transaction(first.getId(),null,"20",LocalDate.now());
        List<Integer> outcomes=concurrent(
                () -> new TransactionTemplate(manager).execute(s -> transactions.attachIfAvailable(entry.getId(),first.getId(),target.getId(),LocalDateTime.now())),
                () -> new TransactionTemplate(manager).execute(s -> projects.deleteIfUnreferenced(target.getId())));
        assertEquals(1,outcomes.stream().mapToInt(Integer::intValue).sum());
        Long linked=transactions.findById(entry.getId()).orElseThrow().getProjectId();
        assertTrue(linked==null || projects.existsById(linked));
    }
    private List<Integer> concurrent(Callable<Integer> firstAction,Callable<Integer> secondAction) throws Exception {
        try (ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start=new CountDownLatch(1);
            Future<Integer> a=executor.submit(() -> { start.await(); return firstAction.call(); });
            Future<Integer> b=executor.submit(() -> { start.await(); return secondAction.call(); }); start.countDown();
            return List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    Association association() { Association value=new Association();String slug="scope-"+UUID.randomUUID();value.setName(slug);value.setSlug(slug);value.setType("APS");value.setEmail(slug+"@example.org");value.setPassword("fixture-only");return associations.saveAndFlush(value); }
    Project project(Long associationId) { Project value=new Project();value.setAssociationId(associationId);value.setName("Scoped project");return projects.saveAndFlush(value); }
    Transaction transaction(Long associationId,Long project,String amount,LocalDate date) { Transaction value=new Transaction();value.setAssociationId(associationId);value.setProjectId(project);value.setAmount(new BigDecimal(amount));value.setDate(date);value.setType(TransactionType.INCOME);value.setDescription("Synthetic fixture");return transactions.saveAndFlush(value); }
    void journal(Long associationId) { JournalEntry value=new JournalEntry();value.setAssociationId(associationId);value.setDate(LocalDate.of(2026,1,1));value.setDescription("Fixture");journal.saveAndFlush(value); }
    static Path temp() { try { return Files.createTempDirectory("associago-financial-scope-"); } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); } }
}
