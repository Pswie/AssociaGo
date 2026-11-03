package com.associago.stats;

import com.associago.esercizio.Esercizio;
import com.associago.activity.Activity;
import com.associago.activity.ActivityAccess;
import com.associago.activity.ActivityParticipant;
import com.associago.activity.ActivityCost;
import com.associago.access.SuiteAssociationScope;
import com.associago.activity.repository.ActivityCostRepository;
import com.associago.activity.repository.ActivityParticipantRepository;
import com.associago.activity.repository.ActivityRepository;
import com.associago.assembly.repository.AssemblyRepository;
import com.associago.assembly.repository.AssemblyParticipantRepository;
import com.associago.assembly.repository.AssemblyMotionRepository;
import com.associago.event.Event;
import com.associago.event.repository.EventRepository;
import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.stats.dto.ActivityStatsDTO;
import com.associago.stats.dto.AssemblyStatsDTO;
import com.associago.stats.dto.FinancialStatsDTO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import com.associago.suite.SuiteException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final TransactionRepository transactionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityParticipantRepository activityParticipantRepository;
    private final ActivityCostRepository activityCostRepository;
    private final AssemblyRepository assemblyRepository;
    private final UserAssociationRepository memberships;
    private final Environment environment;
    private final ActivityAccess activityAccess;
    private final AssemblyParticipantRepository assemblyParticipants;
    private final AssemblyMotionRepository assemblyMotions;

    public StatsService(MemberRepository memberRepository,
                        EventRepository eventRepository,
                        TransactionRepository transactionRepository,
                        ActivityRepository activityRepository,
                        ActivityParticipantRepository activityParticipantRepository,
                        ActivityCostRepository activityCostRepository,
                        AssemblyRepository assemblyRepository) {
        this(memberRepository, eventRepository, transactionRepository, activityRepository,
                activityParticipantRepository, activityCostRepository, assemblyRepository, null, null, null, null, null);
    }

    @Autowired
    public StatsService(MemberRepository memberRepository, EventRepository eventRepository,
                        TransactionRepository transactionRepository, ActivityRepository activityRepository,
                        ActivityParticipantRepository activityParticipantRepository,
                        ActivityCostRepository activityCostRepository, AssemblyRepository assemblyRepository,
                        UserAssociationRepository memberships, Environment environment, ActivityAccess activityAccess,
                        AssemblyParticipantRepository assemblyParticipants, AssemblyMotionRepository assemblyMotions) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.transactionRepository = transactionRepository;
        this.activityRepository = activityRepository;
        this.activityParticipantRepository = activityParticipantRepository;
        this.activityCostRepository = activityCostRepository;
        this.assemblyRepository = assemblyRepository;
        this.memberships = memberships;
        this.environment = environment;
        this.activityAccess = activityAccess;
        this.assemblyParticipants = assemblyParticipants;
        this.assemblyMotions = assemblyMotions;
    }

    public Map<String, Object> getDashboardStats() {
        return getDashboardStats(null);
    }

    /**
     * I numeri della pagina iniziale, eventualmente di un solo esercizio.
     *
     * <p><strong>Perché il filtro non poteva stare nel browser.</strong> Questo
     * metodo restituisce totali, non righe: una somma già calcolata non si
     * filtra dopo. Per vedere l'anno scorso bisogna che sia il server a
     * sommarne i movimenti.
     *
     * <p>Non tutto segue l'anno, e la differenza è la stessa di sempre. Le
     * <em>somme</em> — entrate, uscite, saldo — sono flussi: appartengono
     * all'esercizio. Il <em>numero di soci</em> è una giacenza: quanti ce ne
     * sono adesso non è una domanda annuale, e restringerla all'anno
     * risponderebbe a una domanda che nessuno ha fatto.
     *
     * @param anno l'esercizio, oppure {@code null} per l'intera storia.
     */
    public Map<String, Object> getDashboardStats(Integer anno) {
        if (central()) return scopedDashboard(anno);
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());

        // --- Members ---
        Map<String, Object> members = new HashMap<>();
        members.put("total", memberRepository.count());
        members.put("active", memberRepository.countByMembershipStatus("ACTIVE"));
        members.put("newThisMonth", memberRepository.countByCreatedAtBetween(startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59)));
        stats.put("members", members);

        // --- Treasury ---
        Map<String, Object> treasury = new HashMap<>();

        // Con un esercizio scelto le somme si fermano a quell'anno; senza,
        // coprono tutta la storia — che è il comportamento di prima.
        LocalDate farPast = Esercizio.valido(anno)
                ? Esercizio.inizio(anno) : LocalDate.of(1900, 1, 1);
        LocalDate farFuture = Esercizio.valido(anno)
                ? Esercizio.fine(anno) : LocalDate.of(2100, 12, 31);

        BigDecimal totalIncome = transactionRepository.sumByTypeAndDateRange(TransactionType.INCOME, farPast, farFuture);
        BigDecimal totalExpense = transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, farPast, farFuture);

        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;

        treasury.put("balance", totalIncome.subtract(totalExpense));

        BigDecimal incomeMonth = transactionRepository.sumByTypeAndDateRange(TransactionType.INCOME, startOfMonth, endOfMonth);
        BigDecimal expenseMonth = transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, startOfMonth, endOfMonth);
        treasury.put("incomeMonth", incomeMonth != null ? incomeMonth : BigDecimal.ZERO);
        treasury.put("expenseMonth", expenseMonth != null ? expenseMonth : BigDecimal.ZERO);
        stats.put("treasury", treasury);

        // --- Events ---
        Map<String, Object> events = new HashMap<>();
        events.put("upcoming", eventRepository.countByStartDatetimeAfter(now));
        Event nextEvent = eventRepository.findFirstByStartDatetimeAfterOrderByStartDatetimeAsc(now);
        if (nextEvent != null) {
            Map<String, Object> nextEventMap = new HashMap<>();
            nextEventMap.put("title", nextEvent.getName());
            nextEventMap.put("date", nextEvent.getStartDatetime());
            events.put("nextEvent", nextEventMap);
        } else {
            events.put("nextEvent", null);
        }
        stats.put("events", events);

        // --- Recent Activity ---
        List<Map<String, Object>> recentActivity = new ArrayList<>();
        stats.put("recentActivity", recentActivity);

        return stats;
    }

    public Map<String, Object> getGeneralStats() {
        return getDashboardStats();
    }

    public ActivityStatsDTO getActivityStats(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new RuntimeException("Activity not found"));
        if (central()) activityAccess.require(activity.getAssociationId());

        ActivityStatsDTO stats = new ActivityStatsDTO();
        stats.setActivityId(activity.getId());
        stats.setActivityName(activity.getName());
        stats.setMaxParticipants(activity.getMaxParticipants());

        // Participants
        Integer totalParticipants = activityParticipantRepository.countByActivityId(activityId);
        stats.setTotalParticipants(totalParticipants);

        if (central()) {
            if (activityAccess.mayReadFinancial()) {
                BigDecimal revenue = activityParticipantRepository.findByActivityId(activityId).stream()
                        .filter(ActivityParticipant::isPaid).map(ActivityParticipant::getAmountPaid)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal costs = activityCostRepository.findByActivityId(activityId).stream()
                        .map(ActivityCost::getAmount).filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                stats.setTotalRevenue(revenue);
                stats.setTotalCosts(costs);
                stats.setNetProfit(revenue.subtract(costs));
                if (revenue.signum() > 0)
                    stats.setProfitMargin(revenue.subtract(costs).divide(revenue, 4, RoundingMode.HALF_UP).doubleValue() * 100);
            }
            if (activity.getMaxParticipants() != null && activity.getMaxParticipants() > 0)
                stats.setOccupancyRate((double) totalParticipants / activity.getMaxParticipants() * 100);
            return stats;
        }

        // Revenue (Sum of payments from participants)
        // Note: Assuming we have a method to sum payments. If not, we iterate or use a custom query.
        // For now, let's assume a custom query exists or we implement it.
        // BigDecimal totalRevenue = activityParticipantRepository.sumAmountPaidByActivityId(activityId);
        // Fallback:
        BigDecimal totalRevenue = BigDecimal.ZERO; // Placeholder, needs repo method
        stats.setTotalRevenue(totalRevenue);

        // Costs
        // BigDecimal totalCosts = activityCostRepository.sumAmountByActivityId(activityId);
        BigDecimal totalCosts = BigDecimal.ZERO; // Placeholder
        stats.setTotalCosts(totalCosts);

        // Net Profit
        stats.setNetProfit(totalRevenue.subtract(totalCosts));

        // Margins
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            stats.setProfitMargin(stats.getNetProfit().divide(totalRevenue, 2, RoundingMode.HALF_UP).doubleValue() * 100);
        } else {
            stats.setProfitMargin(0.0);
        }

        // Occupancy
        if (activity.getMaxParticipants() != null && activity.getMaxParticipants() > 0) {
            stats.setOccupancyRate((double) totalParticipants / activity.getMaxParticipants() * 100);
        } else {
            stats.setOccupancyRate(0.0);
        }

        return stats;
    }

    public AssemblyStatsDTO getAssemblyStats(Long assemblyId) {
        if (central()) {
            var assembly = assemblyRepository.findById(assemblyId)
                    .orElseThrow(() -> new SuiteException(404, "ASSEMBLY_NOT_FOUND", "Assemblea non trovata."));
            SuiteAssociationScope.require(environment, SecurityContextHolder.getContext().getAuthentication(),
                    assembly.getAssociationId(), "associago.assemblies.read");
            var stats = new AssemblyStatsDTO();
            stats.setAssemblyId(assembly.getId());
            stats.setTitle(assembly.getTitle());
            stats.setTotalParticipants((int) assemblyParticipants.findByAssemblyId(assemblyId).stream()
                    .filter(p -> "PRESENT".equals(p.getParticipationType()) || "PROXY".equals(p.getParticipationType())).count());
            stats.setTotalMotions(assemblyMotions.findByAssemblyId(assemblyId).size());
            stats.setQuorumReached(assembly.isQuorumReached());
            // Historical voting eligibility is not reconstructed from today's global member table.
            return stats;
        }
        // Placeholder implementation
        return new AssemblyStatsDTO();
    }

    public FinancialStatsDTO getFinancialStats(Long associationId, int year) {
        if (central()) {
            SuiteAssociationScope.require(environment, SecurityContextHolder.getContext().getAuthentication(),
                    associationId, "associago.finance.read");
            var stats = new FinancialStatsDTO();
            LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
            BigDecimal income = amount(transactionRepository.sumScoped(associationId, TransactionType.INCOME, start, end));
            BigDecimal expenses = amount(transactionRepository.sumScoped(associationId, TransactionType.EXPENSE, start, end));
            stats.setTotalIncome(income);
            stats.setTotalExpenses(expenses);
            stats.setNetBalance(income.subtract(expenses));
            Map<String, BigDecimal> incomeCategories = new HashMap<>(), expenseCategories = new HashMap<>(), paymentMethods = new HashMap<>();
            for (var transaction : transactionRepository.findByAssociationIdAndDateBetween(associationId, start, end)) {
                String category = transaction.getCategory() == null || transaction.getCategory().isBlank() ? "Senza categoria" : transaction.getCategory();
                if (transaction.getType() == TransactionType.INCOME) {
                    incomeCategories.merge(category, amount(transaction.getAmount()), BigDecimal::add);
                    String method = transaction.getPaymentMethod() == null || transaction.getPaymentMethod().isBlank() ? "Non indicato" : transaction.getPaymentMethod();
                    paymentMethods.merge(method, amount(transaction.getAmount()), BigDecimal::add);
                } else if (transaction.getType() == TransactionType.EXPENSE)
                    expenseCategories.merge(category, amount(transaction.getAmount()), BigDecimal::add);
            }
            stats.setIncomeByCategory(incomeCategories);
            stats.setExpensesByCategory(expenseCategories);
            stats.setIncomeByPaymentMethod(paymentMethods);
            return stats;
        }
        // Placeholder implementation
        return new FinancialStatsDTO();
    }

    private boolean central() { return environment != null && SuiteAssociationScope.enabled(environment); }
    private static BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private Map<String, Object> scopedDashboard(Integer anno) {
        Long association = environment.getProperty("SUITE_ASSOCIATION_ID", Long.class, 0L);
        if (association <= 0) throw new SuiteException(503, "ASSOCIATION_CONFIGURATION", "Associazione non configurata.");
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());
        Map<String, Object> stats = new HashMap<>();
        var rows = memberships.findByAssociationId(association);
        Map<String, Object> members = new HashMap<>();
        members.put("total", (long) rows.size());
        members.put("active", rows.stream().filter(row -> "ACTIVE".equals(row.getStatus())).count());
        members.put("newThisMonth", rows.stream().filter(row -> row.getJoinDate() != null &&
                !row.getJoinDate().isBefore(startOfMonth) && !row.getJoinDate().isAfter(endOfMonth)).count());
        stats.put("members", members);
        LocalDate start = Esercizio.valido(anno) ? Esercizio.inizio(anno) : LocalDate.of(1900, 1, 1);
        LocalDate end = Esercizio.valido(anno) ? Esercizio.fine(anno) : LocalDate.of(2100, 12, 31);
        Map<String, Object> treasury = new HashMap<>();
        treasury.put("balance", amount(transactionRepository.sumScoped(association, TransactionType.INCOME, start, end))
                .subtract(amount(transactionRepository.sumScoped(association, TransactionType.EXPENSE, start, end))));
        treasury.put("incomeMonth", amount(transactionRepository.sumScoped(association, TransactionType.INCOME, startOfMonth, endOfMonth)));
        treasury.put("expenseMonth", amount(transactionRepository.sumScoped(association, TransactionType.EXPENSE, startOfMonth, endOfMonth)));
        stats.put("treasury", treasury);
        Map<String, Object> events = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        events.put("upcoming", eventRepository.countByAssociationIdAndStartDatetimeAfter(association, now));
        Event next = eventRepository.findFirstByAssociationIdAndStartDatetimeAfterOrderByStartDatetimeAsc(association, now);
        Map<String, Object> nextView = null;
        if (next != null) {
            nextView = new HashMap<>();
            nextView.put("title", next.getName());
            nextView.put("date", next.getStartDatetime());
        }
        events.put("nextEvent", nextView);
        stats.put("events", events);
        stats.put("recentActivity", List.of());
        return stats;
    }
}
