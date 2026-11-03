package com.associago.access;

import com.associago.activity.*;
import com.associago.activity.repository.*;
import com.associago.assembly.*;
import com.associago.assembly.repository.*;
import com.associago.association.*;
import com.associago.association.dto.AssociationUpdateDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.associago.event.Event;
import com.associago.event.repository.EventRepository;
import com.associago.finance.*;
import com.associago.finance.fiscal.*;
import com.associago.finance.fiscal.repository.*;
import com.associago.finance.repository.*;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.stats.*;
import com.associago.suite.*;
import com.associago.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Two associations, finite grants and a verified actor; no application database is loaded. */
class LegacyCentralScopeTests {
    private static final String OWNER = "user_owner12345678";
    private static final String USER = "user_reader12345678";

    private MockEnvironment environment() {
        var env = new MockEnvironment().withProperty("SUITE_ACCESS_ENABLED", "true")
                .withProperty("SUITE_ASSOCIATION_ID", "1")
                .withProperty("SUITE_ACCESS_OWNER_SUBJECT", OWNER);
        env.setActiveProfiles("cloud");
        return env;
    }

    private JwtAuthenticationToken actor(String subject, String... permissions) {
        var result = new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg", "RS256")
                .subject(subject).build(), Arrays.stream(permissions)
                .map(permission -> new SimpleGrantedAuthority("SCOPE_associago." + permission)).toList());
        SecurityContextHolder.getContext().setAuthentication(result);
        return result;
    }

    private FinancialAccess financial(MockEnvironment env) {
        return new FinancialAccess(mock(SuiteAccess.class), env);
    }

    private void forbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(403, assertThrows(SuiteException.class, action).getStatus());
    }

    private PaymentMethod method(Long id, Long association, String configuration) {
        var value = new PaymentMethod();
        value.setId(id); value.setAssociationId(association); value.setName("Bank");
        value.setType("BANK_TRANSFER"); value.setConfiguration(configuration);
        return value;
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void paymentReadsAreScopedAndCredentialRedactionNeverMutatesTheEntity() {
        var env = environment();
        var service = mock(PaymentMethodService.class);
        var controller = new PaymentMethodController(service, financial(env), env);
        var reader = actor(USER, "finance.read");
        var own = method(10L, 1L, "private-provider-configuration");
        when(service.getAllByAssociation(1L)).thenReturn(List.of(own));
        when(service.getActiveByAssociation(1L)).thenReturn(List.of(own));
        when(service.getById(10L)).thenReturn(own);
        when(service.getById(20L)).thenReturn(method(20L, 2L, "other-association"));
        assertNull(controller.getAll(1L, reader).getBody().getFirst().getConfiguration());
        assertNull(controller.getActive(1L, reader).getBody().getFirst().getConfiguration());
        assertNull(controller.getById(10L, reader).getBody().getConfiguration());
        assertEquals("private-provider-configuration", own.getConfiguration());
        forbidden(() -> controller.getAll(2L, reader));
        forbidden(() -> controller.getById(20L, reader));
        verify(service, never()).getAllByAssociation(2L);
    }

    @Test void paymentReadGrantCannotCreateUpdateOrDelete() {
        var env = environment();
        var service = mock(PaymentMethodService.class);
        var controller = new PaymentMethodController(service, financial(env), env);
        var reader = actor(USER, "finance.read");
        when(service.getById(10L)).thenReturn(method(10L, 1L, null));
        forbidden(() -> controller.create(method(null, 1L, null), reader));
        forbidden(() -> controller.update(10L, method(null, 1L, null), reader));
        forbidden(() -> controller.delete(10L, reader));
        verify(service, never()).create(any());
        verify(service, never()).update(any(), any());
        verify(service, never()).delete(any());
    }

    @Test void paymentOrdinaryEditsPreserveSecretsAndCannotReassignOrMergeAnotherRecord() {
        var env = environment();
        var service = mock(PaymentMethodService.class);
        var controller = new PaymentMethodController(service, financial(env), env);
        var editor = actor(USER, "finance.read", "finance.write");
        when(service.getById(10L)).thenReturn(method(10L, 1L, "kept-secret"));
        when(service.updateWithoutConfiguration(eq(10L), eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(2));
        var response = controller.update(10L, method(null, 1L, null), editor).getBody();
        assertNull(response.getConfiguration());
        verify(service).updateWithoutConfiguration(eq(10L), eq(1L), argThat(value -> value.getConfiguration() == null));
        verify(service, never()).update(any(), any());
        forbidden(() -> controller.update(10L, method(null, 2L, null), editor));
        forbidden(() -> controller.update(10L, method(null, 1L, "replace"), editor));
        forbidden(() -> controller.create(method(null, 1L, "new-secret"), editor));
        forbidden(() -> controller.delete(10L, editor));
        assertEquals(400, assertThrows(SuiteException.class,
                () -> controller.create(method(20L, 1L, null), editor)).getStatus());
        forbidden(() -> controller.create(method(null, 2L, null), editor));
        verify(service, never()).delete(any());
    }

    @Test void onlyPinnedOwnerCanReadAndReplaceProviderConfiguration() {
        var env = environment();
        var service = mock(PaymentMethodService.class);
        var controller = new PaymentMethodController(service, financial(env), env);
        var owner = actor(OWNER, "finance.read", "finance.write");
        when(service.getById(10L)).thenReturn(method(10L, 1L, "owner-secret"));
        when(service.update(eq(10L), any())).thenAnswer(invocation -> invocation.getArgument(1));
        assertEquals("owner-secret", controller.getById(10L, owner).getBody().getConfiguration());
        assertEquals("replacement", controller.update(10L, method(null, 1L, "replacement"), owner)
                .getBody().getConfiguration());
        forbidden(() -> controller.getAll(2L, owner));
    }

    @Test void fiscalReadAndWriteCheckTheActualAssociationAndIgnoreBrowserAuthor() {
        var env = environment();
        var service = mock(FiscalService.class);
        var identities = mock(ClerkIdentityService.class);
        var controller = new FiscalController(service, financial(env), identities, env);
        var reader = actor(USER, "finance.read");
        when(service.getFiscalYearClosure(1L, 2026)).thenReturn(Optional.empty());
        assertEquals(404, controller.getClosure(1L, 2026, reader).getStatusCode().value());
        forbidden(() -> controller.getClosure(2L, 2026, reader));
        forbidden(() -> controller.getInvoiceCheckHistory(2L, reader));
        forbidden(() -> controller.closeFiscalYear(1L, 2026, 999L, reader));
        var editor = actor(USER, "finance.read", "finance.write");
        var person = new User(); person.setId(7L);
        when(identities.resolve(editor)).thenReturn(Optional.of(person));
        controller.closeFiscalYear(1L, 2026, 999L, editor);
        controller.runInvoiceCheck(1L, 2026, 999L, editor);
        verify(service).closeFiscalYear(1L, 2026, 7L);
        verify(service).performInvoiceCheck(1L, 2026, 7L);
        verify(service, never()).closeFiscalYear(anyLong(), anyInt(), eq(999L));
        when(identities.resolve(editor)).thenReturn(Optional.empty());
        forbidden(() -> controller.runInvoiceCheck(1L, 2026, 999L, editor));
        forbidden(() -> controller.runInvoiceCheck(2L, 2026, 999L, editor));
    }

    @Test void fiscalClosureNeverAddsTransactionsFromTheOtherAssociation() {
        var closures = mock(FiscalYearClosureRepository.class);
        var transactions = mock(TransactionRepository.class);
        when(closures.findByAssociationIdAndYear(1L, 2026)).thenReturn(Optional.empty());
        when(closures.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.sumScoped(eq(1L), eq(TransactionType.INCOME), any(), any())).thenReturn(new BigDecimal("100"));
        when(transactions.sumScoped(eq(1L), eq(TransactionType.EXPENSE), any(), any())).thenReturn(new BigDecimal("35"));
        var service = new FiscalService(closures, mock(FiscalComparisonRepository.class),
                mock(InvoiceCheckRepository.class), transactions, mock(InvoiceRepository.class));
        var result = service.closeFiscalYear(1L, 2026, 7L);
        assertEquals(new BigDecimal("65"), result.getNetBalance());
        assertEquals(1L, result.getAssociationId());
        verify(transactions, never()).sumByTypeAndDateRange(any(), any(), any());
        verify(transactions, never()).sumScoped(eq(2L), any(), any(), any());
        assertThrows(IllegalArgumentException.class, () -> service.closeFiscalYear(null, 2026, 7L));
    }

    @Test void associationIdsCannotEscapeTheConfiguredInstanceAndManagersCannotDeleteIt() {
        var service = mock(AssociationService.class);
        var controller = new AssociationController(service);
        ReflectionTestUtils.setField(controller, "suiteAccessEnabled", true);
        ReflectionTestUtils.setField(controller, "suiteAssociationId", 1L);
        ReflectionTestUtils.setField(controller, "suiteOwnerSubject", OWNER);
        var association = new Association(); association.setId(1L);
        when(service.findById(1L)).thenReturn(Optional.of(association));
        var manager = actor(USER, "settings.read", "settings.write");
        assertEquals(1, controller.getAllAssociations().size());
        assertEquals(200, controller.getAssociationById(1L, manager).getStatusCode().value());
        forbidden(() -> controller.getAssociationById(2L, manager));
        forbidden(() -> controller.getLogo(2L, manager));
        forbidden(() -> controller.updateAssociation(2L, null, manager));
        forbidden(() -> controller.uploadLogo(2L, null, manager));
        forbidden(() -> controller.deleteAssociation(2L, manager));
        forbidden(() -> controller.deleteAssociation(1L, manager));
        forbidden(() -> controller.createAssociation(null));
        forbidden(() -> controller.setupAssociation(null));
        var databaseChange = new ObjectMapper().convertValue(Map.of("dbHost", "other.example.test"), AssociationUpdateDTO.class);
        forbidden(() -> controller.updateAssociation(1L, databaseChange, manager));
        verify(service, never()).findAll();
        verify(service, never()).findById(2L);
        verify(service, never()).delete(any());
    }

    private class StatsFixture {
        final MockEnvironment env = environment();
        final MemberRepository globalMembers = mock(MemberRepository.class);
        final EventRepository events = mock(EventRepository.class);
        final TransactionRepository transactions = mock(TransactionRepository.class);
        final ActivityRepository activities = mock(ActivityRepository.class);
        final ActivityParticipantRepository participants = mock(ActivityParticipantRepository.class);
        final ActivityCostRepository costs = mock(ActivityCostRepository.class);
        final AssemblyRepository assemblies = mock(AssemblyRepository.class);
        final AssemblyParticipantRepository assemblyParticipants = mock(AssemblyParticipantRepository.class);
        final AssemblyMotionRepository motions = mock(AssemblyMotionRepository.class);
        final UserAssociationRepository memberships = mock(UserAssociationRepository.class);
        final ActivityAccess activityAccess = new ActivityAccess(env, mock(ClerkIdentityService.class), memberships, financial(env));
        final StatsService service = new StatsService(globalMembers, events, transactions, activities,
                participants, costs, assemblies, memberships, env, activityAccess, assemblyParticipants, motions);
        StatsFixture() { when(memberships.findByAssociationId(1L)).thenReturn(List.of()); }
    }

    @Test void dashboardUsesAssociationLedgerScopedMoneyAndScopedUpcomingEvents() {
        var f = new StatsFixture();
        var current = new UserAssociation(); current.setStatus("ACTIVE"); current.setJoinDate(LocalDate.now());
        var suspended = new UserAssociation(); suspended.setStatus("SUSPENDED");
        when(f.memberships.findByAssociationId(1L)).thenReturn(List.of(current, suspended));
        when(f.transactions.sumScoped(eq(1L), eq(TransactionType.INCOME), any(), any())).thenReturn(new BigDecimal("20"));
        when(f.transactions.sumScoped(eq(1L), eq(TransactionType.EXPENSE), any(), any())).thenReturn(new BigDecimal("5"));
        when(f.events.countByAssociationIdAndStartDatetimeAfter(eq(1L), any())).thenReturn(1L);
        var event = new Event(); event.setName("Own event"); event.setStartDatetime(LocalDateTime.now().plusDays(1));
        when(f.events.findFirstByAssociationIdAndStartDatetimeAfterOrderByStartDatetimeAsc(eq(1L), any())).thenReturn(event);
        var result = f.service.getDashboardStats(2026);
        assertEquals(2L, ((Map<?, ?>) result.get("members")).get("total"));
        assertEquals(1L, ((Map<?, ?>) result.get("members")).get("active"));
        assertEquals(new BigDecimal("15"), ((Map<?, ?>) result.get("treasury")).get("balance"));
        assertEquals(1L, ((Map<?, ?>) result.get("events")).get("upcoming"));
        verifyNoInteractions(f.globalMembers);
        verify(f.transactions, never()).sumByTypeAndDateRange(any(), any(), any());
        verify(f.events, never()).countByStartDatetimeAfter(any());
        verify(f.events, never()).findFirstByStartDatetimeAfterOrderByStartDatetimeAsc(any());
        verify(f.memberships, never()).findByAssociationId(2L);
    }

    @Test void generalStatsUseTheSamePermissionRedactionAsDashboard() {
        var f = new StatsFixture();
        var dashboard = new DashboardController(f.service);
        ReflectionTestUtils.setField(dashboard, "suiteAccessEnabled", true);
        var controller = new StatsController(f.service, dashboard, financial(f.env), f.env);
        var reader = actor(USER, "dashboard.read");
        var result = controller.getGeneralStats(reader).getBody();
        assertFalse(result.containsKey("members"));
        assertFalse(result.containsKey("treasury"));
        assertFalse(result.containsKey("events"));
        assertFalse(result.containsKey("recentActivity"));
    }

    @Test void financialStatsCheckScopeBeforeQueryingAndReturnRealAnnualTotals() {
        var f = new StatsFixture();
        var reader = actor(USER, "finance.read");
        var controller = new StatsController(f.service, new DashboardController(f.service), financial(f.env), f.env);
        forbidden(() -> controller.getFinancialStats(2L, 2026, reader));
        when(f.transactions.sumScoped(eq(1L), eq(TransactionType.INCOME), any(), any())).thenReturn(new BigDecimal("130"));
        when(f.transactions.sumScoped(eq(1L), eq(TransactionType.EXPENSE), any(), any())).thenReturn(new BigDecimal("30"));
        when(f.transactions.findByAssociationIdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());
        assertEquals(new BigDecimal("100"), controller.getFinancialStats(1L, 2026, reader).getBody().getNetBalance());
        verify(f.transactions, never()).sumScoped(eq(2L), any(), any(), any());
        verify(f.transactions, never()).findByAssociationIdAndDateBetween(eq(2L), any(), any());
    }

    @Test void activityStatsCheckRealParentAndHideMoneyWithoutFinancialPermission() {
        var f = new StatsFixture();
        actor(USER, "activities.read");
        var own = new Activity(); own.setId(10L); own.setAssociationId(1L); own.setName("Own"); own.setMaxParticipants(4);
        var foreign = new Activity(); foreign.setId(20L); foreign.setAssociationId(2L);
        when(f.activities.findById(10L)).thenReturn(Optional.of(own));
        when(f.activities.findById(20L)).thenReturn(Optional.of(foreign));
        when(f.participants.countByActivityId(10L)).thenReturn(2);
        forbidden(() -> f.service.getActivityStats(20L));
        var result = f.service.getActivityStats(10L);
        assertEquals(2, result.getTotalParticipants());
        assertEquals(50.0, result.getOccupancyRate());
        assertNull(result.getTotalRevenue()); assertNull(result.getTotalCosts()); assertNull(result.getNetProfit());
        verify(f.participants, never()).countByActivityId(20L);
        verifyNoInteractions(f.costs);
        verify(f.participants, never()).findByActivityId(any());
        actor(USER, "activities.read", "finance.read");
        var participant = new ActivityParticipant(); participant.setPaid(true); participant.setAmountPaid(new BigDecimal("80"));
        var cost = new ActivityCost(); cost.setAmount(new BigDecimal("20"));
        when(f.participants.findByActivityId(10L)).thenReturn(List.of(participant));
        when(f.costs.findByActivityId(10L)).thenReturn(List.of(cost));
        assertEquals(new BigDecimal("60"), f.service.getActivityStats(10L).getNetProfit());
    }

    @Test void assemblyStatsUseTheActualParentAndDoNotInventHistoricalVoterCounts() {
        var f = new StatsFixture();
        actor(USER, "assemblies.read");
        var own = new Assembly(); own.setId(10L); own.setAssociationId(1L); own.setTitle("Own assembly");
        var foreign = new Assembly(); foreign.setId(20L); foreign.setAssociationId(2L);
        when(f.assemblies.findById(10L)).thenReturn(Optional.of(own));
        when(f.assemblies.findById(20L)).thenReturn(Optional.of(foreign));
        var present = new AssemblyParticipant(); present.setParticipationType("PRESENT");
        var absent = new AssemblyParticipant(); absent.setParticipationType("ABSENT");
        when(f.assemblyParticipants.findByAssemblyId(10L)).thenReturn(List.of(present, absent));
        when(f.motions.findByAssemblyId(10L)).thenReturn(List.of(new AssemblyMotion()));
        forbidden(() -> f.service.getAssemblyStats(20L));
        var result = f.service.getAssemblyStats(10L);
        assertEquals(1, result.getTotalParticipants()); assertEquals(1, result.getTotalMotions());
        assertNull(result.getEligibleVoters()); assertNull(result.getAttendanceRate());
        verifyNoInteractions(f.globalMembers);
        verify(f.assemblyParticipants, never()).findByAssemblyId(20L);
    }

    @Test void policyDisabledKeepsTheExistingDesktopPaymentBehavior() {
        var service = mock(PaymentMethodService.class);
        var legacy = new PaymentMethodController(service);
        var existing = method(20L, 2L, "desktop-configuration");
        when(service.getById(20L)).thenReturn(existing);
        assertSame(existing, legacy.getById(20L, null).getBody());
        assertEquals("desktop-configuration", legacy.getById(20L, null).getBody().getConfiguration());
    }
}
