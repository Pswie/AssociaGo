package com.associago.project;

import com.associago.finance.dto.TransactionDTO;
import com.associago.finance.mapper.TransactionMapper;
import com.associago.finance.repository.TransactionRepository;
import com.associago.suite.SuiteAccess;
import com.associago.suite.SuiteException;
import com.associago.suite.SuiteProjectLinkRepository;
import com.associago.volunteer.VolunteerService;
import com.associago.volunteer.VolunteerShift;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{id}")
public class ProjectWorkspaceController {
    public record AssignRequest(String personType, Long personId) { }
    private final ProjectAssignmentService assignments;
    private final SuiteProjectLinkRepository links;
    private final VolunteerShiftRepository shifts;
    private final VolunteerService volunteers;
    private final TransactionRepository transactions;
    private final SuiteAccess access;
    private final com.associago.project.repository.ProjectRepository projects;

    public ProjectWorkspaceController(ProjectAssignmentService assignments, SuiteProjectLinkRepository links,
                                     VolunteerShiftRepository shifts, VolunteerService volunteers,
                                     TransactionRepository transactions, SuiteAccess access,
                                     com.associago.project.repository.ProjectRepository projects) {
        this.assignments = assignments;
        this.links = links;
        this.shifts = shifts;
        this.volunteers = volunteers;
        this.transactions = transactions;
        this.access = access;
        this.projects = projects;
    }

    @GetMapping("/suite")
    public Map<String, String> link(@PathVariable long id, Authentication authentication) {
        assignments.project(id, authentication);
        return Collections.singletonMap("suiteProjectId", links.findByProjectId(id).map(item -> item.getSuiteProjectId()).orElse(null));
    }

    @GetMapping("/candidates")
    public List<ProjectAssignmentService.Candidate> candidates(@PathVariable long id, Authentication authentication) {
        return assignments.candidates(id, authentication);
    }

    @GetMapping("/assignments")
    public List<ProjectAssignmentService.AssignmentView> list(@PathVariable long id, Authentication authentication) {
        return assignments.list(id, authentication);
    }

    @PostMapping("/assignments")
    public ProjectAssignment assign(@PathVariable long id, @RequestBody AssignRequest body, Authentication authentication) {
        return assignments.assign(id, body.personType(), body.personId(), authentication);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public void remove(@PathVariable long id, @PathVariable long assignmentId, Authentication authentication) {
        assignments.remove(id, assignmentId, authentication);
    }

    @GetMapping("/shifts")
    public List<VolunteerShift> shifts(@PathVariable long id, Authentication authentication) {
        assignments.project(id, authentication);
        return shifts.findByProjectId(id);
    }

    @PostMapping("/shifts")
    public VolunteerShift createShift(@PathVariable long id, @RequestBody VolunteerShift shift, Authentication authentication) {
        assignments.project(id, authentication);
        shift.setId(null);
        shift.setProjectId(id);
        shift.setVerifiedBy(null);
        shift.setVerifiedAt(null);
        assignments.validateShift(shift);
        return volunteers.createShift(shift);
    }

    @GetMapping("/transactions")
    public List<TransactionDTO> transactions(@PathVariable long id, Authentication authentication) {
        Project project = assignments.project(id, authentication);
        financial(authentication);
        return transactions.findByProjectId(id).stream().filter(item -> project.getAssociationId().equals(item.getAssociationId()))
                .map(TransactionMapper::toDTO).toList();
    }

    @PostMapping("/transactions/{transactionId}")
    @Transactional
    public TransactionDTO attach(@PathVariable long id, @PathVariable long transactionId, Authentication authentication) {
        Project project = assignments.project(id, authentication);
        financial(authentication);
        projects.lockForFinancialWrite(id);
        if (!projects.existsById(id)) throw new SuiteException(404,"PROJECT_MISSING","Progetto non più disponibile.");
        var transaction = transactions.findById(transactionId)
                .orElseThrow(() -> new SuiteException(404, "TRANSACTION_MISSING", "Movimento non trovato."));
        if (!project.getAssociationId().equals(transaction.getAssociationId())) throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Movimento di un'altra associazione.");
        if (transaction.getProjectId() != null && transaction.getProjectId() != id) {
            throw new SuiteException(409, "TRANSACTION_LINKED", "Il movimento è già attribuito a un altro progetto.");
        }
        if (transactions.attachIfAvailable(transactionId,project.getAssociationId(),id,LocalDateTime.now())!=1)
            throw new SuiteException(409,"TRANSACTION_CHANGED","Movimento attribuito da un'altra richiesta o progetto non più disponibile.");
        return TransactionMapper.toDTO(transactions.findById(transactionId).orElseThrow(() -> new SuiteException(404,"TRANSACTION_MISSING","Movimento non trovato.")));
    }

    @GetMapping("/transactions/available")
    public List<TransactionDTO> available(@PathVariable long id, Authentication authentication) {
        Project project = assignments.project(id, authentication);
        financial(authentication);
        return transactions.findAvailableForProject(project.getAssociationId(), id).stream().map(TransactionMapper::toDTO).toList();
    }

    private void financial(Authentication authentication) {
        if (!access.mayReadFinancial(authentication)) throw new SuiteException(403, "FINANCIAL_FORBIDDEN", "Il ruolo non consente di consultare i movimenti contabili.");
    }
}
