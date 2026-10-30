package com.associago.project;

import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.project.dto.ProjectBalance;
import com.associago.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.associago.suite.SuiteException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Progetti e loro rendiconto.
 *
 * @since 0.2.0
 */
@Service
public class ProjectService {

    private static final Set<String> VALID_STATUSES = Set.of(
            Project.PROPOSTO, Project.ATTIVO, Project.DA_COMPLETARE, Project.COMPLETATO);

    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
    private com.associago.suite.SuiteProjectLinkRepository suiteLinks;
    private com.associago.project.repository.ProjectAssignmentRepository assignments;
    private com.associago.volunteer.repository.VolunteerShiftRepository shifts;

    @org.springframework.beans.factory.annotation.Autowired
    public void setWorkspaceRepositories(com.associago.suite.SuiteProjectLinkRepository links,
            com.associago.project.repository.ProjectAssignmentRepository assignments,
            com.associago.volunteer.repository.VolunteerShiftRepository shifts) {
        this.suiteLinks = links;
        this.assignments = assignments;
        this.shifts = shifts;
    }

    public ProjectService(ProjectRepository projectRepository,
                          TransactionRepository transactionRepository) {
        this.projectRepository = projectRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<Project> findByAssociation(Long associationId) {
        return projectRepository.findByAssociationIdOrderByDeadlineAsc(associationId);
    }

    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    @Transactional
    public Project save(Project project) {
        validate(project);
        return projectRepository.save(project);
    }

    @Transactional
    public Optional<Project> update(Long id, Project details) {
        return projectRepository.findById(id).map(existing -> {
            existing.setName(details.getName());
            existing.setStatus(details.getStatus());
            existing.setArea(details.getArea());
            existing.setReferent(details.getReferent());
            existing.setFundingSource(details.getFundingSource());
            existing.setOpportunityUrl(details.getOpportunityUrl());
            existing.setBudget(details.getBudget());
            existing.setDeadline(details.getDeadline());
            existing.setNeeds(details.getNeeds());
            existing.setNotes(details.getNotes());
            validate(existing);
            return projectRepository.save(existing);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        // Preserve both accounting entries and their historical project attribution.
        if (!projectRepository.existsById(id)) {
            return false;
        }
        projectRepository.lockForFinancialWrite(id);
        if (suiteLinks != null && suiteLinks.findByProjectId(id).isPresent()
                || assignments != null && assignments.countByProjectId(id) > 0
                || shifts != null && !shifts.findByProjectId(id).isEmpty()
                || transactionRepository.existsByProjectId(id)) {
            throw new SuiteException(409,"PROJECT_REFERENCED","Il progetto ha movimenti, collegamenti o presenze storiche: conservarlo e aggiornarne lo stato, senza eliminarlo.");
        }
        if (projectRepository.deleteIfUnreferenced(id)==1) return true;
        if (!projectRepository.existsById(id)) return false;
        throw new SuiteException(409,"PROJECT_REFERENCED","Il progetto ha ricevuto un collegamento da un'altra richiesta: non è stato eliminato.");
    }

    /**
     * Rendiconto di un progetto: entrate, uscite, saldo e scostamento dal
     * budget dichiarato.
     */
    public Optional<ProjectBalance> balance(Long projectId, LocalDate today) {
        return projectRepository.findById(projectId).map(project -> {
            BigDecimal income = transactionRepository
                    .sumByProjectAndAssociationAndType(projectId, project.getAssociationId(), TransactionType.INCOME);
            BigDecimal expense = transactionRepository
                    .sumByProjectAndAssociationAndType(projectId, project.getAssociationId(), TransactionType.EXPENSE);
            return ProjectBalance.of(project, income, expense, today);
        });
    }

    public List<ProjectBalance> balances(Long associationId, LocalDate today) {
        return findByAssociation(associationId).stream()
                .map(project -> ProjectBalance.of(
                        project,
                        transactionRepository.sumByProjectAndAssociationAndType(project.getId(), project.getAssociationId(), TransactionType.INCOME),
                        transactionRepository.sumByProjectAndAssociationAndType(project.getId(), project.getAssociationId(), TransactionType.EXPENSE),
                        today))
                .toList();
    }

    private void validate(Project project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new IllegalArgumentException("Il nome del progetto è obbligatorio");
        }
        if (project.getAssociationId() == null) {
            throw new IllegalArgumentException("Il progetto deve appartenere a un'associazione");
        }
        if (!VALID_STATUSES.contains(project.getStatus())) {
            throw new IllegalArgumentException(
                    "Stato non ammesso: " + project.getStatus() + ". Ammessi: " + VALID_STATUSES);
        }
        if (project.getBudget() != null && project.getBudget().signum() < 0) {
            throw new IllegalArgumentException("Il budget non può essere negativo");
        }
    }
}
