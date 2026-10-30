package com.associago.attendance;

import com.associago.activity.Activity;
import com.associago.activity.repository.ActivityRepository;
import com.associago.association.repository.AssociationRepository;
import com.associago.attendance.dto.*;
import com.associago.attendance.mapper.AttendanceMapper;
import com.associago.attendance.repository.AttendanceRecordRepository;
import com.associago.attendance.repository.AttendanceSessionRepository;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.suite.SuiteException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AttendanceService {
    private static final Set<String> STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED");
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final ActivityRepository activities;
    private final UserAssociationRepository memberships;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final AttendanceAccess access;

    public AttendanceService(AttendanceSessionRepository sessions, AttendanceRecordRepository records,
                             ActivityRepository activities, UserAssociationRepository memberships,
                             MemberRepository members, AssociationRepository associations, AttendanceAccess access) {
        this.sessions = sessions;
        this.records = records;
        this.activities = activities;
        this.memberships = memberships;
        this.members = members;
        this.associations = associations;
        this.access = access;
    }

    public List<AttendanceSession> getSessionsByActivity(Long activityId, Authentication actor, Long selected) {
        activity(activityId, actor, selected);
        return sessions.findByActivityId(activityId);
    }

    public AttendanceSession getSessionById(Long id, Authentication actor, Long selected) {
        return session(id, actor, selected, false).session();
    }

    public List<AttendanceParticipantDTO> participants(Long activityId, Authentication actor, Long selected) {
        Long association = activity(activityId, actor, selected).getAssociationId();
        List<AttendanceParticipantDTO> candidates = new ArrayList<>();
        for (var membership : memberships.findByAssociationId(association)) {
            if (membership.getUser() == null) continue;
            var user = membership.getUser();
            candidates.add(new AttendanceParticipantDTO("MEMBERSHIP", membership.getId(), null,
                    name(user.getFirstName(), user.getLastName(), "Iscrizione #" + membership.getId()),
                    membership.getMembershipCardNumber(), membership.getStatus()));
        }
        if (legacyAllowed(association)) {
            for (var member : members.findAll()) {
                candidates.add(new AttendanceParticipantDTO("LEGACY_MEMBER", null, member.getId(),
                        name(member.getFirstName(), member.getLastName(), "Anagrafica storica #" + member.getId()),
                        null, member.getMembershipStatus()));
            }
        }
        candidates.sort(Comparator.comparing(AttendanceParticipantDTO::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AttendanceParticipantDTO::participantType)
                .thenComparing(candidate -> candidate.membershipId() == null ? candidate.memberId() : candidate.membershipId()));
        return candidates;
    }

    @Transactional
    public AttendanceSession createSession(AttendanceSessionUpsertDTO dto, Authentication actor, Long selected) {
        Activity activity = activity(dto.activityId(), actor, selected);
        AttendanceSession entity = AttendanceMapper.toEntity(dto);
        validateSession(entity, activity.getAssociationId(), true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        return sessions.save(entity);
    }

    @Transactional
    public AttendanceSession updateSession(Long id, AttendanceSessionUpsertDTO dto, Authentication actor, Long selected) {
        SessionScope scope = session(id, actor, selected, true);
        AttendanceSession entity = scope.session();
        immutable(dto.activityId(), entity.getActivityId(), "ATTENDANCE_ACTIVITY_IMMUTABLE");
        boolean newInstructor = !Objects.equals(entity.getInstructorId(), dto.instructorId());
        AttendanceMapper.updateEntity(entity, dto);
        validateSession(entity, scope.association(), newInstructor);
        entity.setUpdatedAt(LocalDateTime.now());
        return sessions.save(entity);
    }

    @Transactional
    public void deleteSession(Long id, Authentication actor, Long selected) {
        AttendanceSession entity = session(id, actor, selected, true).session();
        // Cloud historical baselines have no cascading attendance FK.
        records.deleteBySessionId(id);
        sessions.delete(entity);
    }

    public List<AttendanceRecord> getRecordsBySession(Long id, Authentication actor, Long selected) {
        session(id, actor, selected, false);
        return records.findBySessionId(id);
    }

    @Transactional
    public AttendanceRecord createRecord(AttendanceRecordUpsertDTO dto, Authentication actor, Long selected) {
        SessionScope scope = session(dto.sessionId(), actor, selected, true);
        AttendanceRecord entity = AttendanceMapper.toEntity(dto);
        if (entity.getStatus() == null) entity.setStatus("PRESENT");
        validateRecord(entity, scope.association(), true);
        return records.save(entity);
    }

    @Transactional
    public AttendanceRecord updateRecord(Long id, AttendanceRecordUpsertDTO dto, Authentication actor, Long selected) {
        AttendanceRecord entity = record(id);
        SessionScope scope = session(entity.getSessionId(), actor, selected, true);
        immutable(dto.sessionId(), entity.getSessionId(), "ATTENDANCE_SESSION_IMMUTABLE");
        immutable(dto.memberId(), entity.getMemberId(), "ATTENDANCE_PARTICIPANT_IMMUTABLE");
        immutable(dto.membershipId(), entity.getMembershipId(), "ATTENDANCE_PARTICIPANT_IMMUTABLE");
        var previousTime = entity.getCheckInTime();
        String previousNotes = entity.getNotes();
        AttendanceMapper.updateEntity(entity, dto);
        // Older clients send a status-only partial update without participant
        // references. Preserve their omitted optional data; full UI writes
        // include the unchanged explicit participant and may clear these fields.
        if (dto.memberId() == null && dto.membershipId() == null) {
            if (dto.checkInTime() == null) entity.setCheckInTime(previousTime);
            if (dto.notes() == null) entity.setNotes(previousNotes);
        }
        validateRecord(entity, scope.association(), false);
        return records.save(entity);
    }

    @Transactional
    public void deleteRecord(Long id, Authentication actor, Long selected) {
        AttendanceRecord entity = record(id);
        session(entity.getSessionId(), actor, selected, true);
        records.delete(entity);
    }

    private Activity activity(Long id, Authentication actor, Long selected) {
        positive(id, "ATTENDANCE_ACTIVITY_REQUIRED");
        Activity entity = activities.findById(id).orElseThrow(() -> missing("Attività non trovata."));
        access.require(actor, entity.getAssociationId(), selected);
        return entity;
    }

    private SessionScope session(Long id, Authentication actor, Long selected, boolean lock) {
        positive(id, "ATTENDANCE_SESSION_REQUIRED");
        AttendanceSession entity = (lock ? sessions.findForUpdate(id) : sessions.findById(id))
                .orElseThrow(() -> missing("Sessione non trovata."));
        Activity activity = activity(entity.getActivityId(), actor, selected);
        return new SessionScope(entity, activity.getAssociationId());
    }

    private AttendanceRecord record(Long id) {
        positive(id, "ATTENDANCE_RECORD_REQUIRED");
        return records.findById(id).orElseThrow(() -> missing("Presenza non trovata."));
    }

    private void validateSession(AttendanceSession entity, Long association, boolean newInstructor) {
        if (entity.getDate() == null) throw invalid("ATTENDANCE_DATE_REQUIRED", "Indicare la data della sessione.");
        if (entity.getStartTime() != null && entity.getEndTime() != null && entity.getEndTime().isBefore(entity.getStartTime())) {
            throw invalid("ATTENDANCE_TIME_RANGE", "L'orario di fine non può precedere l'inizio.");
        }
        notes(entity.getNotes());
        if (entity.getInstructorId() != null) validateLegacy(entity.getInstructorId(), association, newInstructor);
    }

    private void validateRecord(AttendanceRecord entity, Long association, boolean creating) {
        if ((entity.getMemberId() == null) == (entity.getMembershipId() == null)) {
            throw invalid("ATTENDANCE_PARTICIPANT_REQUIRED", "Indicare una sola anagrafica partecipante.");
        }
        if (!STATUSES.contains(entity.getStatus() == null ? "" : entity.getStatus())) {
            throw invalid("ATTENDANCE_STATUS_INVALID", "Stato della presenza non valido.");
        }
        notes(entity.getNotes());
        Long excluded = entity.getId() == null ? -1L : entity.getId();
        boolean duplicate;
        if (entity.getMembershipId() != null) {
            positive(entity.getMembershipId(), "ATTENDANCE_PARTICIPANT_REQUIRED");
            var participant = memberships.findById(entity.getMembershipId())
                    .orElseThrow(() -> missing("Iscrizione partecipante non trovata."));
            if (participant.getUser() == null || participant.getAssociation() == null
                    || !Objects.equals(association, participant.getAssociation().getId())) {
                throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Il partecipante non appartiene all'associazione dell'attività.");
            }
            duplicate = records.existsBySessionIdAndMembershipIdAndIdNot(entity.getSessionId(), entity.getMembershipId(), excluded);
        } else {
            validateLegacy(entity.getMemberId(), association, creating);
            duplicate = creating && records.existsBySessionIdAndMemberIdAndIdNot(entity.getSessionId(), entity.getMemberId(), excluded);
        }
        if (duplicate) throw new SuiteException(409, "ATTENDANCE_DUPLICATE", "Il partecipante ha già una presenza in questa sessione.");
    }

    private void validateLegacy(Long memberId, Long association, boolean newReference) {
        positive(memberId, "ATTENDANCE_PARTICIPANT_REQUIRED");
        if (newReference && !legacyAllowed(association)) {
            throw new SuiteException(409, "LEGACY_PARTICIPANT_SCOPE", "Usare un'iscrizione associativa per identificare il partecipante.");
        }
        if (!members.existsById(memberId)) throw missing("Anagrafica storica non trovata.");
        // Legacy rows carry no association. They remain readable/updatable through
        // their authorized parent session; new cross-association references fail closed.
    }

    private boolean legacyAllowed(Long association) {
        return !access.cloud() || associations.count() == 1 && associations.existsById(association);
    }
    private static void immutable(Long requested, Long actual, String code) {
        if (requested != null && !Objects.equals(requested, actual)) {
            throw new SuiteException(409, code, "La presenza deve mantenere l'attività, la sessione e il partecipante originali.");
        }
    }
    private static void positive(Long id, String code) {
        if (id == null || id <= 0) throw invalid(code, "Identificativo richiesto non valido.");
    }
    private static void notes(String value) {
        if (value != null && value.length() > 255) throw invalid("ATTENDANCE_NOTES_TOO_LONG", "Le note possono contenere al massimo 255 caratteri.");
    }
    private static String name(String first, String last, String fallback) {
        String value = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return value.isBlank() ? fallback : value;
    }
    private static SuiteException invalid(String code, String message) { return new SuiteException(400, code, message); }
    private static SuiteException missing(String message) { return new SuiteException(404, "ATTENDANCE_NOT_FOUND", message); }
    private record SessionScope(AttendanceSession session, Long association) {}
}
