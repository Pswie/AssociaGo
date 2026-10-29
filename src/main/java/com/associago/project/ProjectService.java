package com.associago.project;

import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.project.dto.ProjectBalance;
import com.associago.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // I movimenti restano: cancellare un progetto non deve far sparire
        // righe di prima nota, che sono documenti contabili. Perdono solo il
        // riferimento al progetto.
        if (!projectRepository.existsById(id)) {
            return false;
        }
        transactionRepository.findByProjectId(id).forEach(transaction -> {
            transaction.setProjectId(null);
            transactionRepository.save(transaction);
        });
        projectRepository.deleteById(id);
        return true;
    }

    /**
     * Rendiconto di un progetto: entrate, uscite, saldo e scostamento dal
     * budget dichiarato.
     */
    public Optional<ProjectBalance> balance(Long projectId, LocalDate today) {
        return projectRepository.findById(projectId).map(project -> {
            BigDecimal income = transactionRepository
                    .sumByProjectAndType(projectId, TransactionType.INCOME);
            BigDecimal expense = transactionRepository
                    .sumByProjectAndType(projectId, TransactionType.EXPENSE);
            return ProjectBalance.of(project, income, expense, today);
        });
    }

    public List<ProjectBalance> balances(Long associationId, LocalDate today) {
        return findByAssociation(associationId).stream()
                .map(project -> ProjectBalance.of(
                        project,
                        transactionRepository.sumByProjectAndType(project.getId(), TransactionType.INCOME),
                        transactionRepository.sumByProjectAndType(project.getId(), TransactionType.EXPENSE),
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
