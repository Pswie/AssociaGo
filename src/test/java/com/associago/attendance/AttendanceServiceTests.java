package com.associago.attendance;

import com.associago.activity.Activity;
import com.associago.activity.repository.ActivityRepository;
import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.attendance.dto.*;
import com.associago.attendance.mapper.AttendanceMapper;
import com.associago.attendance.repository.*;
import com.associago.member.Member;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.suite.SuiteException;
import com.associago.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendanceServiceTests {
    AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    ActivityRepository activities = mock(ActivityRepository.class);
    UserAssociationRepository memberships = mock(UserAssociationRepository.class);
    MemberRepository members = mock(MemberRepository.class);
    AssociationRepository associations = mock(AssociationRepository.class);
    AttendanceAccess access = mock(AttendanceAccess.class);
    AttendanceService service = new AttendanceService(sessions, records, activities, memberships, members, associations, access);
    AttendanceSession session;
    UserAssociation participant;
    @BeforeEach void setup() {
        var activity = new Activity(); activity.setId(5L); activity.setAssociationId(1L);
        when(activities.findById(5L)).thenReturn(Optional.of(activity));
        session = new AttendanceSession(); session.setId(9L); session.setActivityId(5L); session.setDate(LocalDate.of(2026, 10, 30));
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        when(sessions.findForUpdate(9L)).thenReturn(Optional.of(session));
        var association = new Association(); association.setId(1L);
        var user = new User(); user.setId(7L); user.setFirstName("Fixture"); user.setLastName("Participant"); user.setEmail("private@example.invalid");
        participant = new UserAssociation(); participant.setId(301L); participant.setAssociation(association); participant.setUser(user);
        when(memberships.findById(301L)).thenReturn(Optional.of(participant));
        when(memberships.findByAssociationId(1L)).thenReturn(List.of(participant));
        when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void explicitMembershipReferenceNeverBecomesSameNumberedLegacyMember() {
        var saved = service.createRecord(dto(null, 301L, "PRESENT"), null, 1L);
        assertEquals(301L, saved.getMembershipId()); assertNull(saved.getMemberId());
        assertEquals(301L, AttendanceMapper.toDTO(saved).membershipId());
        verify(members, never()).existsById(anyLong());
    }
    @Test void candidatesKeepBothNamespacesWithoutLeakingFullUser() {
        var member = new Member(); member.setId(301L); member.setFirstName("Same"); member.setLastName("Number");
        when(members.findAll()).thenReturn(List.of(member));
        var candidates = service.participants(5L, null, 1L);
        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(row -> "MEMBERSHIP".equals(row.participantType()) && row.memberId() == null));
        assertTrue(candidates.stream().anyMatch(row -> "LEGACY_MEMBER".equals(row.participantType()) && row.membershipId() == null));
        assertFalse(candidates.toString().contains("private@example.invalid"));
    }
    @Test void foreignAssociationMembershipIsRejectedBeforeSave() {
        participant.getAssociation().setId(2L);
        assertEquals(403, assertThrows(SuiteException.class, () -> service.createRecord(dto(null, 301L, "PRESENT"), null, 1L)).getStatus());
        verify(records, never()).save(any());
    }
    @Test void exactlyOneParticipantAndValidStatusAreRequired() {
        assertEquals(400, assertThrows(SuiteException.class, () -> service.createRecord(dto(301L, 301L, "PRESENT"), null, 1L)).getStatus());
        assertEquals(400, assertThrows(SuiteException.class, () -> service.createRecord(dto(null, null, "PRESENT"), null, 1L)).getStatus());
        assertEquals(400, assertThrows(SuiteException.class, () -> service.createRecord(dto(null, 301L, "UNSUPPORTED"), null, 1L)).getStatus());
    }
    @Test void duplicatesUseLockedSessionAndAreConflictResponses() {
        when(records.existsBySessionIdAndMembershipIdAndIdNot(9L, 301L, -1L)).thenReturn(true);
        assertEquals(409, assertThrows(SuiteException.class, () -> service.createRecord(dto(null, 301L, "PRESENT"), null, 1L)).getStatus());
        verify(sessions).findForUpdate(9L); verify(records, never()).save(any());
    }
    @Test void omittedParticipantIdsPreserveIdentityButExplicitRebindingFails() {
        var existing = new AttendanceRecord(); existing.setId(22L); existing.setSessionId(9L); existing.setMembershipId(301L); existing.setStatus("PRESENT");
        when(records.findById(22L)).thenReturn(Optional.of(existing));
        assertEquals(301L, service.updateRecord(22L, new AttendanceRecordUpsertDTO(null, null, "LATE", null, "updated", null), null, 1L).getMembershipId());
        assertEquals("updated", service.updateRecord(22L, new AttendanceRecordUpsertDTO(null, null, "ABSENT", null, null, null), null, 1L).getNotes());
        assertEquals(409, assertThrows(SuiteException.class, () -> service.updateRecord(22L, dto(301L, null, "PRESENT"), null, 1L)).getStatus());
        assertEquals(409, assertThrows(SuiteException.class, () -> service.updateRecord(22L, new AttendanceRecordUpsertDTO(10L, null, "PRESENT", null, null, null), null, 1L)).getStatus());
    }
    @Test void multiAssociationCloudRejectsNewLegacyReferencesWithoutDeletingHistory() {
        when(access.cloud()).thenReturn(true); when(associations.count()).thenReturn(2L);
        assertEquals(409, assertThrows(SuiteException.class, () -> service.createRecord(dto(301L, null, "PRESENT"), null, 1L)).getStatus());
        assertEquals(1, service.participants(5L, null, 1L).size());
        var old = new AttendanceRecord(); old.setId(22L); old.setSessionId(9L); old.setMemberId(301L); old.setStatus("PRESENT");
        when(records.findById(22L)).thenReturn(Optional.of(old)); when(members.existsById(301L)).thenReturn(true);
        assertEquals("ABSENT", service.updateRecord(22L, new AttendanceRecordUpsertDTO(null, null, "ABSENT", null, null, null), null, 1L).getStatus());
    }
    @Test void sessionRebindingAndInvalidTimeRangeFailWhileNullableFieldsCanBeCleared() {
        assertEquals(409, assertThrows(SuiteException.class, () -> service.updateSession(9L, new AttendanceSessionUpsertDTO(6L, null, null, null, null, null), null, 1L)).getStatus());
        assertEquals(400, assertThrows(SuiteException.class, () -> service.createSession(new AttendanceSessionUpsertDTO(5L, session.getDate(), LocalTime.NOON, LocalTime.of(11, 0), null, null), null, 1L)).getStatus());
        session.setNotes("old");
        assertNull(service.updateSession(9L, new AttendanceSessionUpsertDTO(null, null, null, null, null, null), null, 1L).getNotes());
    }
    @Test void sessionDeletionRemovesChildrenExplicitlyForCloudBaselines() {
        service.deleteSession(9L, null, 1L);
        var order = inOrder(records, sessions); order.verify(records).deleteBySessionId(9L); order.verify(sessions).delete(session);
    }
    private AttendanceRecordUpsertDTO dto(Long member, Long membership, String status) {
        return new AttendanceRecordUpsertDTO(9L, member, status, null, null, membership);
    }
}
