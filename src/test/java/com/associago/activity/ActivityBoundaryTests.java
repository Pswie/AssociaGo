package com.associago.activity;

import com.associago.activity.dto.ActivityCreateDTO;
import com.associago.activity.repository.*;
import com.associago.association.Association;
import com.associago.attendance.AttendanceSession;
import com.associago.attendance.repository.AttendanceSessionRepository;
import com.associago.finance.FinancialAccess;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.*;
import com.associago.user.User;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityBoundaryTests {
    ActivityRepository activities = mock(ActivityRepository.class);
    ActivityParticipantRepository participants = mock(ActivityParticipantRepository.class);
    ActivityCostRepository costs = mock(ActivityCostRepository.class);
    ActivityInstructorRepository instructors = mock(ActivityInstructorRepository.class);
    ActivityScheduleRepository schedules = mock(ActivityScheduleRepository.class);
    AttendanceSessionRepository attendance = mock(AttendanceSessionRepository.class);
    ActivityService service;
    Activity activity;
    @BeforeEach void setup() {
        var env = new MockEnvironment(); env.setActiveProfiles("cloud");
        var identities = mock(ClerkIdentityService.class); var memberships = mock(UserAssociationRepository.class);
        var user = new User(); user.setId(7L); var association = new Association(); association.setId(1L);
        var membership = new UserAssociation(); membership.setUser(user); membership.setAssociation(association);
        when(identities.resolve(any())).thenReturn(Optional.of(user));
        when(memberships.findByUserIdAndAssociationId(7L, 1L)).thenReturn(Optional.of(membership));
        var access = new ActivityAccess(env, identities, memberships,
                new FinancialAccess(new SuiteAccess(new SuiteSettings(env), env, identities, memberships), env));
        service = new ActivityService(activities, participants, costs, instructors, schedules, attendance, access);
        activity = new Activity(); activity.setId(5L); activity.setAssociationId(1L); activity.setName("Activity"); activity.setCost(new BigDecimal("20"));
        var foreign = new Activity(); foreign.setId(6L); foreign.setAssociationId(2L);
        when(activities.findById(5L)).thenReturn(Optional.of(activity)); when(activities.findForUpdate(5L)).thenReturn(Optional.of(activity));
        when(activities.findById(6L)).thenReturn(Optional.of(foreign)); when(activities.findForUpdate(6L)).thenReturn(Optional.of(foreign));
        when(activities.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(instructors.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ActivityAccessTests.actor("ROLE_COORDINATORE_SERVIZI");
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }
    @Test void operationalDetailsRedactFinanceAndPersonalFieldsWithoutMutatingManagedRows() {
        var user = new User(); user.setId(91L); user.setFirstName("Participant"); user.setEmail("private@example.invalid");
        var participant = new ActivityParticipant(); participant.setId(301L); participant.setUser(user); participant.setRegistrationDate(LocalDate.now()); participant.setAmountPaid(BigDecimal.TEN); participant.setPaid(true);
        var instructor = new ActivityInstructor(); instructor.setCompensation(new BigDecimal("100"));
        var cost = new ActivityCost(); cost.setAmount(new BigDecimal("15")); cost.setCategory("Venue");
        when(participants.findByActivityId(5L)).thenReturn(List.of(participant));
        when(instructors.findByActivityId(5L)).thenReturn(List.of(instructor)); when(costs.findByActivityId(5L)).thenReturn(List.of(cost));
        var view = service.getActivityWithDetails(5L);
        assertNull(view.getActivity().getCost()); assertNull(view.getTotalCosts()); assertNull(view.getTotalRevenue()); assertNull(view.getNetProfit()); assertNull(view.getPaymentStatus());
        assertNull(view.getInstructors().getFirst().getCompensation()); assertNull(view.getRecentParticipants().getFirst().paid());
        assertFalse(view.getRecentParticipants().toString().contains("private@example.invalid"));
        assertEquals(new BigDecimal("20"), activity.getCost()); assertEquals(new BigDecimal("100"), instructor.getCompensation()); assertTrue(participant.isPaid());
    }
    @Test void activityUpdateChecksOriginalAssociationRejectsRebindingAndPreservesHiddenCost() {
        assertEquals(409, assertThrows(SuiteException.class, () -> service.update(5L, dto(2L, null))).getStatus());
        assertEquals(403, assertThrows(SuiteException.class, () -> service.update(6L, dto(1L, null))).getStatus());
        assertNull(service.update(5L, dto(null, null)).getCost()); assertEquals(new BigDecimal("20"), activity.getCost());
        assertEquals(403, assertThrows(SuiteException.class, () -> service.update(5L, dto(null, BigDecimal.ONE))).getStatus());
    }
    @Test void childWriteIdsCannotOverwriteAnotherActivityAndFinancialCallsDenyOperators() {
        var schedule = new ActivitySchedule(); schedule.setActivityId(5L); schedule.setId(88L);
        assertEquals(400, assertThrows(SuiteException.class, () -> service.addSchedule(schedule)).getStatus());
        verify(schedules, never()).save(any());
        assertEquals(403, assertThrows(SuiteException.class, () -> service.findCostsByActivityId(5L)).getStatus());
        assertEquals(403, assertThrows(SuiteException.class, () -> service.getActivityFinancialSummary(5L)).getStatus());
    }
    @Test void foreignResourceDeletionAndCrossActivityInstructorAreRejected() {
        var row = new ActivitySchedule(); row.setId(88L); row.setActivityId(6L); when(schedules.findById(88L)).thenReturn(Optional.of(row));
        assertEquals(403, assertThrows(SuiteException.class, () -> service.deleteSchedule(88L)).getStatus()); verify(schedules, never()).delete(any());
        var instructor = new ActivityInstructor(); instructor.setId(99L); instructor.setActivityId(6L); when(instructors.findById(99L)).thenReturn(Optional.of(instructor));
        var ownSchedule = new ActivitySchedule(); ownSchedule.setActivityId(5L); ownSchedule.setInstructorId(99L);
        assertEquals(403, assertThrows(SuiteException.class, () -> service.addSchedule(ownSchedule)).getStatus());
    }
    @Test void instructorOperationalUpdateCannotEraseHiddenCompensation() {
        var row = new ActivityInstructor(); row.setId(8L); row.setActivityId(5L); row.setCompensation(BigDecimal.TEN); row.setCompensationType("HOURLY");
        when(instructors.findById(8L)).thenReturn(Optional.of(row));
        assertNull(service.updateInstructor(8L, new ActivityInstructor()).getCompensation());
        assertEquals(BigDecimal.TEN, row.getCompensation()); assertEquals("HOURLY", row.getCompensationType());
    }
    @Test void cloudActivityWithAttendanceCannotLeaveOrphanedHistory() {
        when(attendance.findByActivityId(5L)).thenReturn(List.of(new AttendanceSession()));
        assertEquals(409, assertThrows(SuiteException.class, () -> service.deleteById(5L)).getStatus()); verify(activities, never()).delete(any());
    }
    private static ActivityCreateDTO dto(Long association, BigDecimal cost) {
        return new ActivityCreateDTO(association, "Updated", null, null, null, null, null, null, null, null, cost, true, null, null, true, false);
    }
}
