package com.associago.activity;

import com.associago.activity.dto.ActivityCreateDTO;
import com.associago.activity.dto.ActivityParticipantDTO;
import com.associago.activity.mapper.ActivityMapper;
import com.associago.activity.repository.*;
import com.associago.attendance.repository.AttendanceSessionRepository;
import com.associago.stats.dto.*;
import com.associago.suite.SuiteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final ActivityParticipantRepository participantRepository;
    private final ActivityCostRepository costRepository;
    private final ActivityInstructorRepository instructorRepository;
    private final ActivityScheduleRepository scheduleRepository;
    private final AttendanceSessionRepository attendance;
    private final ActivityAccess access;

    public ActivityService(ActivityRepository activityRepository, ActivityParticipantRepository participantRepository,
            ActivityCostRepository costRepository, ActivityInstructorRepository instructorRepository,
            ActivityScheduleRepository scheduleRepository, AttendanceSessionRepository attendance, ActivityAccess access) {
        this.activityRepository = activityRepository; this.participantRepository = participantRepository;
        this.costRepository = costRepository; this.instructorRepository = instructorRepository;
        this.scheduleRepository = scheduleRepository; this.attendance = attendance; this.access = access;
    }
    public List<Activity> findByAssociationId(Long associationId) {
        Long scope = access.association(associationId);
        return scope == null ? List.of() : activityRepository.findByAssociationId(scope).stream().map(this::responseActivity).toList();
    }
    public List<Activity> findNellEsercizio(Long associationId, Integer anno) {
        Long scope = access.association(associationId);
        if (scope == null) return List.of();
        List<Activity> rows = com.associago.esercizio.Esercizio.valido(anno)
                ? activityRepository.findNellEsercizio(scope, com.associago.esercizio.Esercizio.inizio(anno), com.associago.esercizio.Esercizio.fine(anno))
                : activityRepository.findByAssociationId(scope);
        return rows.stream().map(this::responseActivity).toList();
    }
    public Optional<Activity> findById(Long id) {
        return activityRepository.findById(id).map(activity -> { access.require(activity.getAssociationId()); return responseActivity(activity); });
    }
    @Transactional
    public Activity save(Activity activity) {
        if (activity.getId() == null) {
            access.requireWrite(activity.getAssociationId());
            if (nonzero(activity.getCost())) access.requireFinancial(activity.getAssociationId());
        } else {
            Activity existing = activity(activity.getId(), true, false);
            access.immutable(activity.getAssociationId(), existing.getAssociationId());
            if (access.cloud()) activity.setAssociationId(existing.getAssociationId());
            financialChange(existing.getCost(), activity.getCost(), existing.getAssociationId());
            if (!access.mayReadFinancial()) activity.setCost(existing.getCost());
        }
        return responseActivity(activityRepository.save(activity));
    }
    @Transactional
    public Activity update(Long id, ActivityCreateDTO dto) {
        Activity existing = activity(id, true, false);
        access.immutable(dto.associationId(), existing.getAssociationId());
        BigDecimal previousCost = existing.getCost();
        financialChange(previousCost, dto.cost(), existing.getAssociationId());
        ActivityMapper.updateEntity(existing, dto);
        if (!access.mayReadFinancial()) existing.setCost(previousCost);
        return responseActivity(activityRepository.save(existing));
    }
    @Transactional
    public void deleteById(Long id) {
        Activity existing = activity(id, true, false);
        if (access.cloud() && !attendance.findByActivityId(id).isEmpty()) {
            throw new SuiteException(409, "ACTIVITY_HAS_ATTENDANCE", "Rimuovere prima le sessioni di presenza collegate all'attivit?.");
        }
        if (!costRepository.findByActivityId(id).isEmpty()
                || participantRepository.findByActivityId(id).stream().anyMatch(this::hasPayments)
                || instructorRepository.findByActivityId(id).stream().anyMatch(this::hasCompensation)) {
            access.requireFinancial(existing.getAssociationId());
        }
        scheduleRepository.deleteByActivityId(id);
        instructorRepository.deleteByActivityId(id);
        participantRepository.deleteByActivityId(id);
        costRepository.deleteByActivityId(id);
        activityRepository.delete(existing);
    }
    public List<ActivityParticipant> findParticipantsByActivityId(Long id) {
        activity(id, false, false);
        return participantRepository.findByActivityId(id);
    }
    public List<com.associago.activity.dto.ActivityParticipantCandidateDTO> participantCandidates(Long id) {
        return access.participantCandidates(activity(id, false, false).getAssociationId());
    }
    public ActivityParticipantDTO participantView(ActivityParticipant row) {
        return ActivityPrivacy.participant(row, access.mayReadFinancial());
    }
    @Transactional
    public ActivityParticipant registerParticipant(ActivityParticipant participant) {
        Activity parent = activity(participant.getActivityId(), true, false);
        access.creating(participant.getId());
        if (access.cloud()) participant.setUser(access.participantUser(participant.getUser() == null ? null : participant.getUser().getId(), parent.getAssociationId()));
        if (hasPayments(participant)) access.requireFinancial(parent.getAssociationId());
        if (participant.getRegistrationDate() == null) participant.setRegistrationDate(LocalDate.now());
        return participantRepository.save(participant);
    }
    @Transactional
    public void removeParticipant(Long id) {
        ActivityParticipant row = participantRepository.findById(id).orElseThrow(() -> missing("Partecipante non trovato."));
        Activity parent = activity(row.getActivityId(), true, false);
        if (hasPayments(row)) access.requireFinancial(parent.getAssociationId());
        participantRepository.delete(row);
    }
    public List<ActivityCost> findCostsByActivityId(Long id) {
        activity(id, false, true);
        return costRepository.findByActivityId(id);
    }
    @Transactional
    public ActivityCost addCost(ActivityCost cost) {
        activity(cost.getActivityId(), true, true); access.creating(cost.getId());
        return costRepository.save(cost);
    }
    @Transactional
    public ActivityCost updateCost(Long id, ActivityCost details) {
        ActivityCost row = costRepository.findById(id).orElseThrow(() -> missing("Costo non trovato."));
        activity(row.getActivityId(), true, true); access.immutable(details.getActivityId(), row.getActivityId());
        row.setDescription(details.getDescription()); row.setAmount(details.getAmount()); row.setCategory(details.getCategory());
        row.setDate(details.getDate()); row.setRecurring(details.isRecurring()); row.setFrequency(details.getFrequency());
        row.setSupplier(details.getSupplier()); row.setNotes(details.getNotes());
        return costRepository.save(row);
    }
    @Transactional
    public void deleteCost(Long id) {
        ActivityCost row = costRepository.findById(id).orElseThrow(() -> missing("Costo non trovato."));
        activity(row.getActivityId(), true, true); costRepository.delete(row);
    }
    public List<ActivityInstructor> findInstructorsByActivityId(Long id) {
        activity(id, false, false);
        return instructorRepository.findByActivityId(id).stream().map(this::responseInstructor).toList();
    }
    @Transactional
    public ActivityInstructor addInstructor(ActivityInstructor row) {
        Activity parent = activity(row.getActivityId(), true, false); access.creating(row.getId());
        if (access.cloud()) access.instructorMembership(row.getUserAssociationId(), parent.getAssociationId());
        if (hasCompensation(row)) access.requireFinancial(parent.getAssociationId());
        return responseInstructor(instructorRepository.save(row));
    }
    @Transactional
    public ActivityInstructor updateInstructor(Long id, ActivityInstructor details) {
        ActivityInstructor row = instructorRepository.findById(id).orElseThrow(() -> missing("Istruttore non trovato."));
        Activity parent = activity(row.getActivityId(), true, false); access.immutable(details.getActivityId(), row.getActivityId());
        access.immutable(details.getUserAssociationId(), row.getUserAssociationId());
        financialChange(row.getCompensation(), details.getCompensation(), parent.getAssociationId());
        if (details.getCompensationType() != null && !Objects.equals(details.getCompensationType(), row.getCompensationType())) access.requireFinancial(parent.getAssociationId());
        row.setFirstName(details.getFirstName()); row.setLastName(details.getLastName()); row.setEmail(details.getEmail());
        row.setPhone(details.getPhone()); row.setSpecialization(details.getSpecialization());
        row.setCertifications(details.getCertifications()); row.setNotes(details.getNotes()); row.setActive(details.isActive());
        if (access.mayReadFinancial()) { row.setCompensation(details.getCompensation()); row.setCompensationType(details.getCompensationType()); }
        return responseInstructor(instructorRepository.save(row));
    }
    @Transactional
    public void deleteInstructor(Long id) {
        ActivityInstructor row = instructorRepository.findById(id).orElseThrow(() -> missing("Istruttore non trovato."));
        Activity parent = activity(row.getActivityId(), true, false);
        if (hasCompensation(row)) access.requireFinancial(parent.getAssociationId());
        if (scheduleRepository.findByActivityId(row.getActivityId()).stream().anyMatch(s -> Objects.equals(s.getInstructorId(), id))) {
            throw new SuiteException(409, "INSTRUCTOR_IN_USE", "Rimuovere prima l'istruttore dagli orari collegati.");
        }
        instructorRepository.delete(row);
    }
    public List<ActivitySchedule> findSchedulesByActivityId(Long id) {
        activity(id, false, false); return scheduleRepository.findByActivityId(id);
    }
    @Transactional
    public ActivitySchedule addSchedule(ActivitySchedule row) {
        activity(row.getActivityId(), true, false); access.creating(row.getId()); validateSchedule(row);
        return scheduleRepository.save(row);
    }
    @Transactional
    public ActivitySchedule updateSchedule(Long id, ActivitySchedule details) {
        ActivitySchedule row = scheduleRepository.findById(id).orElseThrow(() -> missing("Orario non trovato."));
        activity(row.getActivityId(), true, false); access.immutable(details.getActivityId(), row.getActivityId());
        details.setActivityId(row.getActivityId()); validateSchedule(details);
        row.setDayOfWeek(details.getDayOfWeek()); row.setStartTime(details.getStartTime()); row.setEndTime(details.getEndTime());
        row.setLocation(details.getLocation()); row.setInstructorId(details.getInstructorId()); row.setActive(details.isActive());
        return scheduleRepository.save(row);
    }
    @Transactional
    public void deleteSchedule(Long id) {
        ActivitySchedule row = scheduleRepository.findById(id).orElseThrow(() -> missing("Orario non trovato."));
        activity(row.getActivityId(), true, false); scheduleRepository.delete(row);
    }
    private Activity activity(Long id, boolean lock, boolean financial) {
        if (id == null || id <= 0) throw new SuiteException(400, "ACTIVITY_REQUIRED", "Indicare l'attivit?.");
        Activity row = (lock ? activityRepository.findForUpdate(id) : activityRepository.findById(id)).orElseThrow(() -> missing("Attivit? non trovata."));
        if (financial) access.requireFinancial(row.getAssociationId());
        else if (lock) access.requireWrite(row.getAssociationId());
        else access.require(row.getAssociationId());
        return row;
    }
    private void validateSchedule(ActivitySchedule row) {
        if (row.getInstructorId() != null) {
            var instructor = instructorRepository.findById(row.getInstructorId()).orElseThrow(() -> missing("Istruttore non trovato."));
            if (!Objects.equals(instructor.getActivityId(), row.getActivityId())) throw new SuiteException(403, "ACTIVITY_FORBIDDEN", "L'istruttore non appartiene all'attivit?.");
        }
        if (row.getStartTime() != null && row.getEndTime() != null && row.getEndTime().isBefore(row.getStartTime())) throw new SuiteException(400, "ACTIVITY_TIME_RANGE", "L'orario di fine non pu? precedere l'inizio.");
    }
    private void financialChange(BigDecimal previous, BigDecimal requested, Long association) {
        if (requested != null && (previous == null || requested.compareTo(previous) != 0)) access.requireFinancial(association);
    }
    private boolean hasPayments(ActivityParticipant row) {
        return row.isPaid() || nonzero(row.getAmountPaid()) || row.getInvoiceId() != null || row.getPaymentDate() != null || row.getPaymentMethod() != null;
    }
    private boolean hasCompensation(ActivityInstructor row) { return row.getCompensation() != null || row.getCompensationType() != null; }
    private static boolean nonzero(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) != 0; }
    private Activity responseActivity(Activity row) { return access.mayReadFinancial() ? row : ActivityPrivacy.basic(row); }
    private ActivityInstructor responseInstructor(ActivityInstructor row) { return access.mayReadFinancial() ? row : ActivityPrivacy.instructor(row); }
    private static SuiteException missing(String message) { return new SuiteException(404, "ACTIVITY_NOT_FOUND", message); }

    // --- Advanced Reporting & Stats Methods ---

    public ActivityWithDetailsDTO getActivityWithDetails(Long activityId) {
        return details(activityId, false);
    }

    private ActivityWithDetailsDTO details(Long activityId, boolean financialReport) {
        Activity activity = activity(activityId, false, financialReport);

        List<ActivityParticipant> participants = participantRepository.findByActivityId(activityId);
        List<ActivityCost> costs = costRepository.findByActivityId(activityId);
        List<ActivityInstructor> instructors = instructorRepository.findByActivityId(activityId);

        ActivityWithDetailsDTO dto = new ActivityWithDetailsDTO();
        dto.setActivity(activity);
        dto.setInstructors(instructors);

        // Participants Stats
        dto.setTotalParticipants(participants.size());
        dto.setActiveParticipants((int) participants.stream().filter(ActivityParticipant::isActive).count());
        dto.setPendingParticipants((int) participants.stream().filter(p -> !p.isPaid() && p.isActive()).count());
        dto.setWaitingListCount(0);

        // Financial Stats
        BigDecimal paidRevenue = participants.stream()
                .filter(ActivityParticipant::isPaid)
                .map(ActivityParticipant::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Qui si sottraeva `discountAmount` del partecipante. Quella colonna non
        // veniva scritta da nessun percorso del programma — restava al suo
        // valore iniziale, zero — quindi la sottrazione non ha mai cambiato un
        // numero. È stata tolta con lo sconto sulle quote (V11).
        BigDecimal pendingRevenue = participants.stream()
                .filter(p -> !p.isPaid())
                .map(p -> activity.getCost() != null ? activity.getCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostsVal = costs.stream()
                .map(ActivityCost::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setTotalRevenue(paidRevenue.add(pendingRevenue));
        dto.setPaidRevenue(paidRevenue);
        dto.setPendingRevenue(pendingRevenue);
        dto.setTotalCosts(totalCostsVal);
        dto.setNetProfit(dto.getTotalRevenue().subtract(totalCostsVal));

        // Time Stats
        if (activity.getStartDate() != null && activity.getEndDate() != null) {
            long totalDays = ChronoUnit.DAYS.between(activity.getStartDate(), activity.getEndDate());
            long daysPassed = ChronoUnit.DAYS.between(activity.getStartDate(), LocalDate.now());
            if (totalDays > 0) {
                double completion = (double) daysPassed / totalDays * 100;
                dto.setCompletionPercentage(Math.min(100, Math.max(0, completion)));
            }
            dto.setDaysRemaining((int) Math.max(0, totalDays - daysPassed));
            dto.setExpiringSoon(dto.getDaysRemaining() < 7);
        }

        // Spots
        if (activity.getMaxParticipants() != null && activity.getMaxParticipants() > 0) {
            dto.setAvailableSpots(Math.max(0, activity.getMaxParticipants() - dto.getActiveParticipants()));
        } else {
            dto.setAvailableSpots(999); // Unlimited
        }

        // Recent Participants (last 5)
        dto.setRecentParticipants(participants.stream()
                .sorted(Comparator.comparing(ActivityParticipant::getRegistrationDate, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .limit(5)
                .map(this::participantView)
                .collect(Collectors.toList()));

        // Cost Categories
        Map<String, BigDecimal> costsByCategory = costs.stream()
                .collect(Collectors.groupingBy(
                        ActivityCost::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, ActivityCost::getAmount, BigDecimal::add)
                ));

        List<ActivityCostSummaryDTO> costSummary = costsByCategory.entrySet().stream()
                .map(e -> {
                    ActivityCostSummaryDTO s = new ActivityCostSummaryDTO();
                    s.setCategory(e.getKey());
                    s.setTotalAmount(e.getValue());
                    return s;
                })
                .sorted(Comparator.comparing(ActivityCostSummaryDTO::getTotalAmount).reversed())
                .collect(Collectors.toList());
        dto.setTopCostCategories(costSummary);

        // Payment Status
        ActivityPaymentStatusDTO paymentStatus = new ActivityPaymentStatusDTO();
        paymentStatus.setTotalPayments(participants.size());
        paymentStatus.setConfirmedPayments((int) participants.stream().filter(ActivityParticipant::isPaid).count());
        paymentStatus.setPendingPayments((int) participants.stream().filter(p -> !p.isPaid()).count());
        if (participants.size() > 0) {
            paymentStatus.setPaidPercentage((double) paymentStatus.getConfirmedPayments() / participants.size() * 100);
        }
        dto.setPaymentStatus(paymentStatus);

        // Performance Metrics
        ActivityPerformanceMetricsDTO metrics = new ActivityPerformanceMetricsDTO();
        if (activity.getMaxParticipants() != null && activity.getMaxParticipants() > 0) {
            metrics.setOccupancyRate((double) dto.getActiveParticipants() / activity.getMaxParticipants() * 100);
        }
        metrics.setDropOutRate(0.0);
        dto.setPerformanceMetrics(metrics);

        if (!access.mayReadFinancial()) {
            dto.setActivity(ActivityPrivacy.basic(activity));
            dto.setInstructors(instructors.stream().map(ActivityPrivacy::instructor).toList());
            dto.setTotalRevenue(null); dto.setPaidRevenue(null); dto.setPendingRevenue(null);
            dto.setTotalCosts(null); dto.setNetProfit(null); dto.setTopCostCategories(null);
            dto.setPaymentStatus(null); dto.setPendingParticipants(null);
        }
        return dto;
    }

    public ActivityFinancialSummaryDTO getActivityFinancialSummary(Long activityId) {
        ActivityWithDetailsDTO details = details(activityId, true);

        ActivityFinancialSummaryDTO summary = new ActivityFinancialSummaryDTO();
        summary.setActivityId(activityId);
        summary.setActivityName(details.getActivity().getName());
        summary.setTotalRevenue(details.getTotalRevenue());
        summary.setPaidRevenue(details.getPaidRevenue());
        summary.setPendingRevenue(details.getPendingRevenue());
        summary.setTotalCosts(details.getTotalCosts());
        summary.setNetProfit(details.getNetProfit());

        if (details.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = details.getNetProfit()
                    .divide(details.getTotalRevenue(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            summary.setProfitMargin(margin.doubleValue());
        } else {
            summary.setProfitMargin(0.0);
        }

        summary.setParticipantsCount(details.getTotalParticipants());

        if (details.getTotalParticipants() > 0) {
            summary.setAverageFeePerUser(details.getTotalRevenue()
                    .divide(BigDecimal.valueOf(details.getTotalParticipants()), 2, RoundingMode.HALF_UP));
        }

        summary.setCostBreakdown(details.getTopCostCategories());

        // Revenue Breakdown (Simplified)
        List<ActivityRevenueSourceDTO> revenueSources = new ArrayList<>();
        ActivityRevenueSourceDTO registrations = new ActivityRevenueSourceDTO();
        registrations.setSource("REGISTRATIONS");
        registrations.setAmount(details.getTotalRevenue());
        registrations.setCount(details.getTotalParticipants());
        revenueSources.add(registrations);
        summary.setRevenueBreakdown(revenueSources);

        return summary;
    }
}
