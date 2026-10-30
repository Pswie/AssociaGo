package com.associago.project;

import com.associago.member.Member;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.project.repository.ProjectAssignmentRepository;
import com.associago.project.repository.ProjectRepository;
import com.associago.suite.SuiteAccess;
import com.associago.suite.SuiteException;
import com.associago.volunteer.VolunteerShift;
import com.associago.volunteer.repository.VolunteerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectAssignmentService {
    public record Candidate(String personType, Long personId, String name) { }
    public record AssignmentView(Long id, String personType, Long personId, String name) { }

    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final UserAssociationRepository memberships;
    private final VolunteerRepository volunteers;
    private final MemberRepository members;
    private final SuiteAccess access;

    public ProjectAssignmentService(ProjectRepository projects, ProjectAssignmentRepository assignments,
                                    UserAssociationRepository memberships, VolunteerRepository volunteers,
                                    MemberRepository members, SuiteAccess access) {
        this.projects = projects;
        this.assignments = assignments;
        this.memberships = memberships;
        this.volunteers = volunteers;
        this.members = members;
        this.access = access;
    }

    public Project project(long id, Authentication authentication) {
        Project project = projects.findById(id).orElseThrow(() -> new SuiteException(404, "PROJECT_MISSING", "Progetto non trovato."));
        access.requireAssociation(authentication, project.getAssociationId());
        return project;
    }

    public List<Candidate> candidates(long projectId, Authentication authentication) {
        Project project = project(projectId, authentication);
        var positions = memberships.findByAssociationId(project.getAssociationId());
        List<Candidate> result = new ArrayList<>();
        positions.stream().filter(position -> "ACTIVE".equals(position.getStatus()) && position.getUser() != null)
                .forEach(position -> result.add(new Candidate("MEMBER", position.getId(),
                        name(position.getUser().getFirstName(), position.getUser().getLastName()))));
        for (var volunteer : volunteers.findAll()) {
            Member member = volunteer.getMemberId() == null ? null : members.findById(volunteer.getMemberId()).orElse(null);
            if (member == null || !"ACTIVE".equals(volunteer.getStatus())) {
                continue;
            }
            boolean enrolled = positions.stream().filter(position -> "ACTIVE".equals(position.getStatus()) && position.getUser() != null)
                    .anyMatch(position -> member.getFiscalCode() != null && !member.getFiscalCode().isBlank()
                            && member.getFiscalCode().equalsIgnoreCase(position.getUser().getTaxCode()));
            if (enrolled) {
                result.add(new Candidate("VOLUNTEER", volunteer.getId(), name(member.getFirstName(), member.getLastName())));
            }
        }
        return result;
    }

    public List<AssignmentView> list(long projectId, Authentication authentication) {
        project(projectId, authentication);
        List<Candidate> candidates = candidates(projectId, authentication);
        return assignments.findByProjectId(projectId).stream().map(assignment -> new AssignmentView(assignment.getId(),
                assignment.getPersonType(), assignment.getPersonId(), candidates.stream()
                .filter(candidate -> candidate.personType().equals(assignment.getPersonType()) && candidate.personId().equals(assignment.getPersonId()))
                .map(Candidate::name).findFirst().orElse("Assegnazione storica #" + assignment.getPersonId()))).toList();
    }

    @Transactional
    public ProjectAssignment assign(long projectId, String personType, Long personId, Authentication authentication) {
        var candidate = candidates(projectId, authentication).stream()
                .filter(item -> item.personType().equals(personType) && Objects.equals(item.personId(), personId)).findFirst()
                .orElseThrow(() -> new SuiteException(403, "PERSON_NOT_ELIGIBLE", "Persona non assegnabile: verificare iscrizione attiva e codice fiscale del volontario."));
        return assignments.findByProjectIdAndPersonTypeAndPersonId(projectId, candidate.personType(), candidate.personId()).orElseGet(() -> {
            ProjectAssignment assignment = new ProjectAssignment();
            assignment.setProjectId(projectId);
            assignment.setPersonType(candidate.personType());
            assignment.setPersonId(candidate.personId());
            return assignments.saveAndFlush(assignment);
        });
    }

    @Transactional
    public void remove(long projectId, long assignmentId, Authentication authentication) {
        project(projectId, authentication);
        var assignment = assignments.findById(assignmentId).filter(item -> item.getProjectId() == projectId)
                .orElseThrow(() -> new SuiteException(404, "ASSIGNMENT_MISSING", "Assegnazione non trovata nel progetto."));
        assignments.delete(assignment);
    }

    public void validateShift(VolunteerShift shift) {
        if (shift.getProjectId() == null) {
            return;
        }
        if (shift.getStatus() != null && !java.util.Set.of("SCHEDULED", "COMPLETED", "MISSED").contains(shift.getStatus())) {
            throw new SuiteException(400, "INVALID_SHIFT", "Stato del turno non valido.");
        }
        if ("COMPLETED".equals(shift.getStatus()) && shift.getHoursWorked() == null) {
            throw new SuiteException(400, "INVALID_SHIFT", "Registrare le ore prima di completare il turno.");
        }
        if (projects.findById(shift.getProjectId()).isEmpty()) {
            throw new SuiteException(404, "PROJECT_MISSING", "Progetto del turno non trovato.");
        }
        if (shift.getVolunteerId() == null || assignments.findByProjectIdAndPersonTypeAndPersonId(
                shift.getProjectId(), "VOLUNTEER", shift.getVolunteerId()).isEmpty()) {
            throw new SuiteException(409, "VOLUNTEER_NOT_ASSIGNED", "Assegnare prima il volontario al progetto.");
        }
        if (shift.getStartTime() == null || shift.getEndTime() == null
                || shift.getHoursWorked() != null && shift.getHoursWorked().compareTo(BigDecimal.ZERO) < 0
                || shift.getHourlyValue() != null && shift.getHourlyValue().compareTo(BigDecimal.ZERO) < 0
                || shift.getStartTime() != null && shift.getEndTime() != null && !shift.getEndTime().isAfter(shift.getStartTime())) {
            throw new SuiteException(400, "INVALID_SHIFT", "Ore, valore orario e intervallo del turno devono essere validi.");
        }
    }

    private String name(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }
}
