package com.associago.inventory;

import com.associago.esercizio.Esercizio;
import com.associago.finance.FinancialService;
import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import com.associago.inventory.repository.InventoryItemRepository;
import com.associago.inventory.repository.InventoryLoanRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryLoanRepository loanRepository;
    private final FinancialService financialService;
    private final UserAssociationRepository membershipRepository;

    public InventoryService(InventoryItemRepository itemRepository, InventoryLoanRepository loanRepository,
                            FinancialService financialService, UserAssociationRepository membershipRepository) {
        this.itemRepository = itemRepository;
        this.loanRepository = loanRepository;
        this.financialService = financialService;
        this.membershipRepository = membershipRepository;
    }

    public Iterable<InventoryItem> getAllItems() {
        Iterable<InventoryItem> items = itemRepository.findAll();
        items.forEach(this::calculateDepreciation);
        return items;
    }

    /**
     * Gli oggetti posseduti in un dato esercizio.
     *
     * <p>Un oggetto in inventario non è un fatto puntuale: lo si possiede
     * <strong>dall'acquisto in poi</strong>. Un tavolo comprato nel 2026 c'è
     * ancora nel 2027 e in tutti gli anni successivi — filtrarlo per anno di
     * acquisto svuoterebbe l'inventario a ogni primo gennaio.
     *
     * <p>Quindi la regola è una sola: compare se è stato acquistato entro la
     * fine dell'anno guardato. Una data di acquisto assente lo fa comparire
     * sempre, che è il comportamento giusto per la roba che c'è e basta.
     *
     * <p>Il filtro è in memoria e non in una query: {@code InventoryItemRepository}
     * è un {@code CrudRepository} senza metodi di ricerca, e l'inventario di
     * un'associazione sta in poche decine di righe. Se un giorno crescesse, qui
     * si mette una query e il resto non cambia.
     */
    public Iterable<InventoryItem> getItemsDellEsercizio(Integer anno) {
        Iterable<InventoryItem> items = getAllItems();
        if (!Esercizio.valido(anno)) return items;

        LocalDate fine = Esercizio.fine(anno);
        List<InventoryItem> posseduti = new ArrayList<>();
        for (InventoryItem item : items) {
            if (item.getPurchaseDate() == null || !item.getPurchaseDate().isAfter(fine)) {
                posseduti.add(item);
            }
        }
        return posseduti;
    }

    public Optional<InventoryItem> getItemById(Long id) {
        Optional<InventoryItem> itemOpt = itemRepository.findById(id);
        itemOpt.ifPresent(this::calculateDepreciation);
        return itemOpt;
    }

    @Transactional
    public InventoryItem createItem(InventoryItem item) {
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        if (item.getStatus() == null) {
            item.setStatus("AVAILABLE");
        }

        InventoryItem savedItem = itemRepository.save(item);

        // Auto-create transaction if purchased
        if ("PURCHASE".equals(item.getAcquisitionMethod()) &&
            item.getPurchasePrice() != null &&
            item.getPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {

            Transaction transaction = new Transaction();
            transaction.setAssociationId(item.getAssociationId());
            transaction.setDate(item.getPurchaseDate() != null ? item.getPurchaseDate() : java.time.LocalDate.now());
            transaction.setAmount(item.getPurchasePrice());
            transaction.setType(TransactionType.EXPENSE);
            transaction.setDescription("Purchase: " + item.getName());
            transaction.setInventoryItemId(savedItem.getId());
            transaction.setCategory("Inventory"); // Simple string or ID if category management exists

            financialService.saveTransaction(transaction);
        }

        return savedItem;
    }

    @Transactional
    public InventoryItem updateItem(Long id, InventoryItem itemDetails) {
        return itemRepository.findById(id).map(item -> {
            item.setName(itemDetails.getName());
            item.setDescription(itemDetails.getDescription());
            item.setCategory(itemDetails.getCategory());
            item.setQuantity(itemDetails.getQuantity());
            item.setLocation(itemDetails.getLocation());
            item.setAcquisitionMethod(itemDetails.getAcquisitionMethod());
            item.setPurchaseDate(itemDetails.getPurchaseDate());
            item.setPurchasePrice(itemDetails.getPurchasePrice());
            item.setDepreciationYears(itemDetails.getDepreciationYears());
            item.setCondition(itemDetails.getCondition());
            item.setStatus(itemDetails.getStatus());
            item.setUpdatedAt(LocalDateTime.now());
            return itemRepository.save(item);
        }).orElseThrow(() -> new RuntimeException("Item not found"));
    }

    public void deleteItem(Long id) {
        itemRepository.deleteById(id);
    }

    // Loans
    public List<InventoryLoan> getLoansByItem(Long itemId) {
        return loanRepository.findByItemId(itemId);
    }

    @Transactional
    public InventoryLoan createLoan(InventoryLoan loan) {
        if (loan.getId() != null || loan.getItemId() == null || loan.getUserAssociationId() == null) {
            throw new IllegalArgumentException("An item and a membership are required for a new loan.");
        }
        InventoryItem item = itemRepository.findById(loan.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        UserAssociation membership = membershipRepository.findById(loan.getUserAssociationId())
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));
        if (membership.getAssociation() == null
                || !item.getAssociationId().equals(membership.getAssociation().getId())) {
            throw new IllegalArgumentException("The borrower must belong to the item's association.");
        }
        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new IllegalStateException("The borrower must have an active membership.");
        }
        if (!"AVAILABLE".equals(item.getStatus()) && !"LOANED".equals(item.getStatus())) {
            throw new IllegalStateException("This item is not available for lending.");
        }
        int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
        long outstanding = outstandingLoans(item.getId());
        if (outstanding >= quantity) {
            throw new IllegalStateException("All units of this item are already on loan.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (loan.getExpectedReturnDate() != null && loan.getExpectedReturnDate().isBefore(now)) {
            throw new IllegalArgumentException("Expected return date cannot be before the loan date.");
        }
        loan.setLoanDate(now);
        loan.setActualReturnDate(null);
        loan.setStatus("ACTIVE");
        loan.setUpdatedAt(now);
        item.setStatus(outstanding + 1 >= quantity ? "LOANED" : "AVAILABLE");
        item.setUpdatedAt(now);
        itemRepository.save(item);
        return loanRepository.save(loan);
    }

    @Transactional
    public InventoryLoan returnLoan(Long loanId, LocalDateTime returnDate) {
        return loanRepository.findById(loanId).map(loan -> {
            if ("RETURNED".equals(loan.getStatus())) {
                return loan;
            }
            if (!"ACTIVE".equals(loan.getStatus()) && !"OVERDUE".equals(loan.getStatus())) {
                throw new IllegalStateException("Only an active or overdue loan can be returned.");
            }
            LocalDateTime returnedAt = returnDate != null ? returnDate : LocalDateTime.now();
            if (loan.getLoanDate() != null && returnedAt.isBefore(loan.getLoanDate())) {
                throw new IllegalArgumentException("Return date cannot be before the loan date.");
            }
            InventoryItem item = itemRepository.findById(loan.getItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Item not found"));
            loan.setActualReturnDate(returnedAt);
            loan.setStatus("RETURNED");
            loan.setUpdatedAt(LocalDateTime.now());
            InventoryLoan saved = loanRepository.save(loan);
            if ("LOANED".equals(item.getStatus())) {
                item.setStatus("AVAILABLE");
                item.setUpdatedAt(LocalDateTime.now());
                itemRepository.save(item);
            }
            return saved;
        }).orElseThrow(() -> new EntityNotFoundException("Loan not found"));
    }

    private long outstandingLoans(Long itemId) {
        return loanRepository.findByItemId(itemId).stream()
                .filter(loan -> "ACTIVE".equals(loan.getStatus()) || "OVERDUE".equals(loan.getStatus()))
                .count();
    }

    // Depreciation Logic
    private void calculateDepreciation(InventoryItem item) {
        if (item.getPurchasePrice() == null || item.getPurchaseDate() == null || item.getDepreciationYears() == null || item.getDepreciationYears() <= 0) {
            item.setCurrentValue(item.getPurchasePrice());
            return;
        }

        LocalDate now = LocalDate.now();
        long yearsPassed = Math.max(0, ChronoUnit.YEARS.between(item.getPurchaseDate(), now));

        if (yearsPassed >= item.getDepreciationYears()) {
            item.setCurrentValue(BigDecimal.ZERO);
        } else {
            BigDecimal annualDepreciation = item.getPurchasePrice().divide(BigDecimal.valueOf(item.getDepreciationYears()), 2, RoundingMode.HALF_UP);
            BigDecimal totalDepreciation = annualDepreciation.multiply(BigDecimal.valueOf(yearsPassed));
            BigDecimal currentValue = item.getPurchasePrice().subtract(totalDepreciation);
            item.setCurrentValue(currentValue.max(BigDecimal.ZERO));
        }
    }
}
