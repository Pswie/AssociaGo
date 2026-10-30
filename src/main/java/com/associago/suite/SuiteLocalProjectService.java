package com.associago.suite;

import com.associago.association.repository.AssociationRepository;
import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import com.associago.project.Project;
import com.associago.project.repository.ProjectAssignmentRepository;
import com.associago.project.repository.ProjectRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class SuiteLocalProjectService {
    private final SuiteSettings settings;
    private final SuiteProjectLinkRepository links;
    private final ProjectRepository projects;
    private final AssociationRepository associations;
    private final TransactionRepository transactions;
    private final ProjectAssignmentRepository assignments;
    private final VolunteerShiftRepository shifts;
    private final ObjectMapper mapper;

    public SuiteLocalProjectService(SuiteSettings settings, SuiteProjectLinkRepository links, ProjectRepository projects,
                                   AssociationRepository associations, TransactionRepository transactions,
                                   ProjectAssignmentRepository assignments, VolunteerShiftRepository shifts, ObjectMapper mapper) {
        this.settings = settings;
        this.links = links;
        this.projects = projects;
        this.associations = associations;
        this.transactions = transactions;
        this.assignments = assignments;
        this.shifts = shifts;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> upsert(String requestedId, SuitePayloads.Projection input) {
        settings.requireEnabled();
        String id = SuiteSettings.uuid(requestedId, "Progetto condiviso");
        if (!Objects.equals(input.schemaVersion(), 1) || !id.equals(input.suiteProjectId())) {
            throw new SuiteException(400, "INVALID_PROJECTION", "Versione del contratto o identificativo non valido.");
        }
        if (!settings.organizationId().equals(input.organizationId())) {
            throw new SuiteException(403, "ORGANIZATION_MISMATCH", "Organizzazione non consentita.");
        }
        if (input.sourceVersion() == null || input.sourceVersion() < 0 || input.sourceVersion() > Integer.MAX_VALUE || input.title() == null
                || input.title().isBlank() || input.title().length() > 200
                || input.description() != null && input.description().length() > 2000) {
            throw new SuiteException(400, "INVALID_PROJECTION", "Nome, descrizione o versione del progetto non validi.");
        }
        BigDecimal budget = amount(input.plannedBudget());
        SuiteProjectLink link = links.findById(id).orElse(null);
        String fingerprint = fingerprint(input, budget);
        if (link != null) {
            requireOrganization(link);
            if (input.targetLocalId() != null && !input.targetLocalId().isBlank()
                    && !String.valueOf(link.getProjectId()).equals(input.targetLocalId())) {
                throw new SuiteException(409, "LINK_CONFLICT", "Il progetto condiviso è già collegato a un altro record locale.");
            }
            if (input.sourceVersion() < link.getSourceVersion()) {
                throw new SuiteException(409, "STALE_VERSION", "La proiezione ricevuta è precedente all'ultima versione.");
            }
            if (input.sourceVersion().equals(link.getSourceVersion()) && link.getSourceFingerprint() != null) {
                if (!fingerprint.equals(link.getSourceFingerprint())) {
                    throw new SuiteException(409, "VERSION_CONFLICT", "La stessa versione contiene dati differenti.");
                }
                return snapshot(id);
            }
        }
        Project project;
        if (link != null) {
            project = local(link.getProjectId());
        } else {
            if (input.targetLocalId() != null && !input.targetLocalId().isBlank()) {
                project = local(localId(input.targetLocalId()));
            } else {
                if (!associations.existsById(settings.associationId())) {
                    throw new SuiteException(503, "ASSOCIATION_MISSING", "L'associazione configurata non esiste.");
                }
                project = new Project();
                project.setAssociationId(settings.associationId());
                project.setStatus(Project.PROPOSTO);
            }
            var existing = project.getId() == null ? null : links.findByProjectId(project.getId()).orElse(null);
            if (existing != null && !existing.getSuiteProjectId().equals(id)) {
                throw new SuiteException(409, "LINK_CONFLICT", "Il progetto locale appartiene già a un altro progetto condiviso.");
            }
            link = new SuiteProjectLink();
            link.setSuiteProjectId(id);
            link.setOrganizationId(settings.organizationId());
        }
        project.setName(input.title().trim());
        if (budget != null) {
            project.setBudget(budget);
        }
        if (input.projectDeadline() != null) {
            project.setDeadline(input.projectDeadline());
        }
        Project saved = projects.save(project);
        link.setProjectId(saved.getId());
        link.setSourceVersion(input.sourceVersion());
        link.setSourceDescription(input.description());
        link.setSourceFingerprint(fingerprint);
        links.saveAndFlush(link);
        return snapshot(id);
    }

    @Transactional
    public SuiteProjectLink linkExisting(String requestedId, long projectId) {
        checkLinkTarget(requestedId, projectId);
        String id = SuiteSettings.uuid(requestedId, "Progetto condiviso");
        local(projectId);
        SuiteProjectLink link = links.findById(id).orElse(null);
        if (link != null) {
            requireOrganization(link);
            if (link.getProjectId() != projectId) {
                throw new SuiteException(409, "LINK_CONFLICT", "Collegamento già presente su un altro progetto.");
            }
            return link;
        }
        var existing = links.findByProjectId(projectId).orElse(null);
        if (existing != null) {
            throw new SuiteException(409, "LINK_CONFLICT", "Il progetto locale è già collegato.");
        }
        link = new SuiteProjectLink();
        link.setSuiteProjectId(id);
        link.setOrganizationId(settings.organizationId());
        link.setProjectId(projectId);
        return links.saveAndFlush(link);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<SuiteProjectLink> linkedProject(long projectId) {
        local(projectId);
        var result = links.findByProjectId(projectId);
        result.ifPresent(this::requireOrganization);
        return result;
    }

    @Transactional(readOnly = true)
    public void checkLinkTarget(String requestedId, long projectId) {
        String id = SuiteSettings.uuid(requestedId, "Progetto condiviso");
        local(projectId);
        linkedProject(projectId).ifPresent(existing -> {
            if (!id.equals(existing.getSuiteProjectId())) {
                throw new SuiteException(409, "LINK_CONFLICT", "Il progetto locale è già collegato a un altro progetto comune.");
            }
        });
        links.findById(id).ifPresent(existing -> {
            requireOrganization(existing);
            if (existing.getProjectId() != projectId) {
                throw new SuiteException(409, "LINK_CONFLICT", "Il progetto comune è già collegato a un altro progetto locale.");
            }
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(String requestedId) {
        settings.requireEnabled();
        String id = SuiteSettings.uuid(requestedId, "Progetto condiviso");
        SuiteProjectLink link = links.findById(id)
                .orElseThrow(() -> new SuiteException(404, "PROJECT_NOT_LINKED", "Progetto non ancora collegato al gestionale."));
        requireOrganization(link);
        Project project = local(link.getProjectId());
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (var transaction : transactions.findByProjectId(project.getId())) {
            if (!Objects.equals(transaction.getAssociationId(), project.getAssociationId()) || transaction.getAmount() == null) {
                continue;
            }
            if (transaction.getType() == TransactionType.INCOME) {
                income = income.add(transaction.getAmount());
            }
            if (transaction.getType() == TransactionType.EXPENSE) {
                expense = expense.add(transaction.getAmount());
            }
        }
        if (income.signum() < 0 || expense.signum() < 0) {
            throw new SuiteException(409, "LEDGER_REVIEW", "Verificare i consuntivi negativi prima di esportare il riepilogo.");
        }
        BigDecimal hours = shifts.findByProjectId(project.getId()).stream()
                .filter(shift -> "COMPLETED".equals(shift.getStatus()))
                .map(shift -> shift.getHoursWorked() == null ? BigDecimal.ZERO : shift.getHoursWorked())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("organizationId", settings.organizationId());
        snapshot.put("suiteProjectId", id);
        snapshot.put("system", "associago");
        snapshot.put("localId", String.valueOf(project.getId()));
        snapshot.put("sourceVersion", link.getSourceVersion());
        snapshot.put("financial", Map.of("currency", "EUR", "income", income.toPlainString(),
                "expense", expense.toPlainString(), "balance", income.subtract(expense).toPlainString()));
        snapshot.put("workforce", Map.of("assignedCount", assignments.countByProjectId(project.getId()),
                "completedHours", hours.toPlainString()));
        snapshot.put("preparation", null);
        return snapshot;
    }

    public Project local(long id) {
        settings.requireEnabled();
        Project project = projects.findById(id).orElseThrow(() -> new SuiteException(404, "PROJECT_MISSING", "Progetto locale non trovato."));
        if (!Objects.equals(project.getAssociationId(), settings.associationId())) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Il progetto appartiene a un'altra associazione.");
        }
        return project;
    }

    public static long localId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id > 0) {
                return id;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the same public validation error for any invalid ID.
        }
        throw new SuiteException(400, "INVALID_LOCAL_ID", "Identificativo del progetto locale non valido.");
    }

    private void requireOrganization(SuiteProjectLink link) {
        if (!settings.organizationId().equals(link.getOrganizationId())) {
            throw new SuiteException(403, "ORGANIZATION_MISMATCH", "Il collegamento appartiene a un'altra organizzazione.");
        }
    }

    private BigDecimal amount(SuitePayloads.Money money) {
        if (money == null) {
            return null;
        }
        if (!"EUR".equals(money.currency())) {
            throw new SuiteException(409, "CURRENCY_UNSUPPORTED", "Il gestionale supporta consuntivi in EUR: nessuna conversione automatica.");
        }
        try {
            if (money.amount() == null || !money.amount().matches("[0-9]+(?:\\.[0-9]+)?")) {
                throw new IllegalArgumentException();
            }
            BigDecimal amount = new BigDecimal(money.amount());
            if (amount.precision() > 36 || Math.max(0, amount.stripTrailingZeros().scale()) > 2) {
                throw new SuiteException(409, "AMOUNT_PRECISION", "Il preventivo non è rappresentabile in centesimi: verificare l'importo senza arrotondamenti automatici.");
            }
            return amount;
        } catch (SuiteException e) {
            throw e;
        } catch (RuntimeException ignored) {
            throw new SuiteException(400, "INVALID_AMOUNT", "Importo preventivo non valido o con frazioni inferiori al centesimo.");
        }
    }

    private String fingerprint(SuitePayloads.Projection value, BigDecimal budget) {
        try {
            Map<String, Object> fields = new TreeMap<>();
            fields.put("title", value.title().trim());
            fields.put("description", value.description());
            fields.put("budget", budget == null ? null : budget.stripTrailingZeros().toPlainString());
            fields.put("deadline", value.projectDeadline());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(fields)));
        } catch (Exception e) { throw new IllegalStateException("Impossibile verificare la proiezione.", e); }
    }
}
