package com.associago.stats;

import com.associago.esercizio.Esercizio;
import com.associago.activity.Activity;
import com.associago.activity.repository.ActivityCostRepository;
import com.associago.activity.repository.ActivityParticipantRepository;
import com.associago.activity.repository.ActivityRepository;
import com.associago.assembly.repository.AssemblyRepository;
import com.associago.event.Event;
import com.associago.event.repository.EventRepository;
import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.member.repository.MemberRepository;
import com.associago.stats.dto.ActivityStatsDTO;
import com.associago.stats.dto.AssemblyStatsDTO;
import com.associago.stats.dto.FinancialStatsDTO;
import org.springframework.stereotype.Service;

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

    public StatsService(MemberRepository memberRepository,
                        EventRepository eventRepository,
                        TransactionRepository transactionRepository,
                        ActivityRepository activityRepository,
                        ActivityParticipantRepository activityParticipantRepository,
                        ActivityCostRepository activityCostRepository,
                        AssemblyRepository assemblyRepository) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.transactionRepository = transactionRepository;
        this.activityRepository = activityRepository;
        this.activityParticipantRepository = activityParticipantRepository;
        this.activityCostRepository = activityCostRepository;
        this.assemblyRepository = assemblyRepository;
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

        ActivityStatsDTO stats = new ActivityStatsDTO();
        stats.setActivityId(activity.getId());
        stats.setActivityName(activity.getName());
        stats.setMaxParticipants(activity.getMaxParticipants());

        // Participants
        Integer totalParticipants = activityParticipantRepository.countByActivityId(activityId);
        stats.setTotalParticipants(totalParticipants);

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
        // Placeholder implementation
        return new AssemblyStatsDTO();
    }

    public FinancialStatsDTO getFinancialStats(Long associationId, int year) {
        // Placeholder implementation
        return new FinancialStatsDTO();
    }
}
