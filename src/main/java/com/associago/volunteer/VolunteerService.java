package com.associago.volunteer;

import com.associago.esercizio.Esercizio;
import com.associago.attachment.Attachment;
import com.associago.attachment.AttachmentOwner;
import com.associago.attachment.AttachmentService;
import com.associago.finance.FinancialService;
import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import com.associago.association.repository.AssociationRepository;
import jakarta.persistence.EntityNotFoundException;
import com.associago.volunteer.repository.VolunteerExpenseRepository;
import com.associago.volunteer.repository.VolunteerRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final VolunteerShiftRepository shiftRepository;
    private final VolunteerExpenseRepository expenseRepository;
    private final FinancialService financialService;
    private final AttachmentService attachments;
    private final AssociationRepository associationRepository;
    private com.associago.project.ProjectAssignmentService projectAssignments;

    @org.springframework.beans.factory.annotation.Autowired
    public void setProjectAssignments(com.associago.project.ProjectAssignmentService service) { projectAssignments = service; }

    public VolunteerService(VolunteerRepository volunteerRepository,
                            VolunteerShiftRepository shiftRepository,
                            VolunteerExpenseRepository expenseRepository,
                            FinancialService financialService,
                            AttachmentService attachments,
                            AssociationRepository associationRepository) {
        this.volunteerRepository = volunteerRepository;
        this.shiftRepository = shiftRepository;
        this.expenseRepository = expenseRepository;
        this.financialService = financialService;
        this.attachments = attachments;
        this.associationRepository = associationRepository;
    }

    public Iterable<Volunteer> getAllVolunteers() {
        return volunteerRepository.findAll();
    }

    /**
     * Chi era volontario in un esercizio, o tutti se l'anno non è indicato.
     *
     * <p>Chi ha cessato compare nel suo anno e non in quelli dopo; chi non ha
     * cessato compare sempre. È la forma che rende il registro capace di
     * rispondere a «chi era volontario nel 2026» — la domanda per cui l'art. 17
     * c.1 lo pretende.
     */
    public Iterable<Volunteer> getVolontariDellEsercizio(Integer anno) {
        return Esercizio.valido(anno)
                ? volunteerRepository.nellEsercizio(Esercizio.inizio(anno), Esercizio.fine(anno))
                : volunteerRepository.findAll();
    }

    public Optional<Volunteer> getVolunteerById(Long id) {
        return volunteerRepository.findById(id);
    }

    @Transactional
    public Volunteer createVolunteer(Volunteer volunteer) {
        volunteer.setCreatedAt(LocalDateTime.now());
        volunteer.setUpdatedAt(LocalDateTime.now());
        if (volunteer.getStatus() == null) {
            volunteer.setStatus("ACTIVE");
        }
        return volunteerRepository.save(volunteer);
    }

    @Transactional
    public Volunteer updateVolunteer(Long id, Volunteer volunteerDetails) {
        return volunteerRepository.findById(id).map(volunteer -> {
            volunteer.setSkills(volunteerDetails.getSkills());
            volunteer.setAvailability(volunteerDetails.getAvailability());
            volunteer.setStatus(volunteerDetails.getStatus());
            volunteer.setNotes(volunteerDetails.getNotes());
            volunteer.setUpdatedAt(LocalDateTime.now());
            return volunteerRepository.save(volunteer);
        }).orElseThrow(() -> new RuntimeException("Volunteer not found"));
    }

    public void deleteVolunteer(Long id) {
        volunteerRepository.deleteById(id);
    }

    // Shifts
    public List<VolunteerShift> getShiftsByVolunteer(Long volunteerId) {
        return shiftRepository.findByVolunteerId(volunteerId);
    }

    public VolunteerShift getShift(Long id) {
        return shiftRepository.findById(id).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Shift not found"));
    }

    @Transactional
    public VolunteerShift createShift(VolunteerShift shift) {
        validateProjectShift(shift);
        if (shift.getStatus() == null) {
            shift.setStatus("SCHEDULED");
        }
        return shiftRepository.save(shift);
    }

    @Transactional
    public VolunteerShift updateShift(Long id, VolunteerShift shiftDetails) {
        return shiftRepository.findById(id).map(shift -> {
            shift.setStartTime(shiftDetails.getStartTime());
            shift.setEndTime(shiftDetails.getEndTime());
            shift.setRole(shiftDetails.getRole());
            shift.setHoursWorked(shiftDetails.getHoursWorked());
            shift.setStatus(shiftDetails.getStatus());
            if (shiftDetails.getProjectId() != null) shift.setProjectId(shiftDetails.getProjectId());
            validateProjectShift(shift);
            return shiftRepository.save(shift);
        }).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Shift not found"));
    }

    private void validateProjectShift(VolunteerShift shift) {
        if (shift.getProjectId() != null) {
            if (projectAssignments == null) throw new IllegalStateException("Project validation unavailable");
            projectAssignments.validateShift(shift);
        }
    }

    // Expenses
    public List<VolunteerExpense> getExpensesByVolunteer(Long volunteerId) {
        return expenseRepository.findByVolunteerId(volunteerId);
    }

    @Transactional
    public VolunteerExpense createExpense(VolunteerExpense expense) {
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        if (expense.getStatus() == null) {
            expense.setStatus("PENDING");
        }
        return expenseRepository.save(expense);
    }

    /**
     * Cambia lo stato di un rimborso, applicando i limiti dell'art. 17 CTS.
     *
     * <p>La verifica sta qui e non sull'inserimento, ed è una scelta: il
     * giustificativo si allega <em>dopo</em> aver creato la riga, perché prima
     * non esiste un identificativo a cui attaccarlo. Controllare in creazione
     * significherebbe rifiutare ogni rimborso analitico, sempre.
     *
     * <p>Il momento giusto è l'approvazione — è lì che qualcuno si assume la
     * responsabilità che le carte siano a posto. Fino ad allora il rimborso
     * resta {@code PENDING} e non produce nessun movimento.
     *
     * <p>Finora {@code Art17Rules} non era invocato da nessuna parte: la regola
     * esisteva, era provata, e non girava mai. Un rimborso analitico si
     * approvava senza ricevuta e nessuno protestava.
     */
    @Transactional
    public VolunteerExpense updateExpenseStatus(Long id, String status, Long associationId) {
        if (status == null || !java.util.Set.of("PENDING", "APPROVED", "REJECTED", "PAID").contains(status)) {
            throw new IllegalArgumentException("Invalid reimbursement status.");
        }
        return expenseRepository.findById(id).map(expense -> {
            if (expense.getTransactionId() != null && !"PAID".equals(status)) {
                throw new IllegalStateException("A paid reimbursement cannot be changed without reversing its transaction.");
            }
            if (associationId != null && expense.getAssociationId() != null
                    && !associationId.equals(expense.getAssociationId())) {
                throw new IllegalArgumentException("The reimbursement belongs to another association.");
            }
            if ("PAID".equals(status) && expense.getTransactionId() == null) {
                Long resolvedAssociation = expense.getAssociationId() != null ? expense.getAssociationId() : associationId;
                if (resolvedAssociation == null || !associationRepository.existsById(resolvedAssociation)) {
                    throw new IllegalArgumentException("A valid association is required to pay a reimbursement.");
                }
                expense.setAssociationId(resolvedAssociation);
            }
            if ("PAID".equals(status) && expense.getTransactionId() != null) {
                return expense;
            }
            if ("APPROVED".equals(status) || "PAID".equals(status)) {
                verificaArt17(expense);
            }
            expense.setStatus(status);
            expense.setUpdatedAt(LocalDateTime.now());

            // If approved and paid, create financial transaction
            if ("PAID".equals(status) && expense.getTransactionId() == null) {
                Transaction transaction = new Transaction();
                transaction.setAssociationId(expense.getAssociationId());
                transaction.setDate(java.time.LocalDate.now());
                transaction.setAmount(expense.getAmount());
                // La base da cui partono le eventuali aggiunte (IVA, spese).
                // Senza, l'invariante `amount = base + aggiunte` andrebbe
                // ricostruita a posteriori dal primo ricalcolo.
                transaction.setBaseAmount(expense.getAmount());
                transaction.setType(TransactionType.EXPENSE);
                transaction.setDescription("Volunteer Reimbursement: " + expense.getDescription());
                transaction.setCategory("Reimbursement");

                Transaction savedTx = financialService.saveTransaction(transaction);
                expense.setTransactionId(savedTx.getId());
            }

            return expenseRepository.save(expense);
        }).orElseThrow(() -> new EntityNotFoundException("Expense not found"));
    }

    /**
     * Applica i limiti dell'art. 17 a un rimborso che sta per essere approvato.
     *
     * @throws IllegalArgumentException con il motivo del rifiuto, che è quello
     *         che l'interfaccia deve mostrare: «non ammissibile» senza dire
     *         perché costringe chi lo legge a indovinare fra tetto giornaliero,
     *         tetto mensile e giustificativo mancante.
     */
    private void verificaArt17(VolunteerExpense expense) {
        // Il giustificativo è un allegato vero, non una stringa: prima la
        // regola guardava `receipt_path`, un percorso su un disco che al
        // riavvio del contenitore si azzerava, e rispondeva «documentato» su
        // una spesa senza documento.
        boolean hasReceipt = attachments.exists(
                AttachmentOwner.VOLUNTEER_EXPENSE, expense.getId(), Attachment.RICEVUTA);

        // Il tetto mensile del forfettario si calcola sull'insieme del mese
        // dello stesso volontario, escluso il rimborso in esame: contarlo due
        // volte lo farebbe sforare da solo.
        LocalDate giorno = expense.getExpenseDate();
        List<VolunteerExpense> stessoMese = giorno == null
                ? List.of()
                : expenseRepository.findByVolunteerId(expense.getVolunteerId()).stream()
                    .filter(altro -> !altro.getId().equals(expense.getId()))
                    .filter(altro -> altro.getExpenseDate() != null
                            && altro.getExpenseDate().getYear() == giorno.getYear()
                            && altro.getExpenseDate().getMonth() == giorno.getMonth())
                    .toList();

        Art17Rules.Check esito = Art17Rules.verify(expense, stessoMese, hasReceipt);
        if (!esito.allowed()) {
            throw new IllegalArgumentException(esito.reason());
        }
    }
}
