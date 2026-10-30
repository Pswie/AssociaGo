package com.associago.finance;

import com.associago.finance.dto.TransactionCreateDTO;
import com.associago.finance.dto.TransactionDTO;
import com.associago.finance.mapper.TransactionMapper;
import com.associago.suite.SuiteException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class FinancialController {

    private final FinancialService financialService;
    private final FinancialAccess access;

    @Autowired
    public FinancialController(FinancialService financialService, FinancialAccess access) {
        this.financialService = financialService;
        this.access = access;
    }

    @PostMapping("/transactions")
    public TransactionDTO createTransaction(@RequestBody TransactionCreateDTO dto,
            @RequestHeader(value = "X-Association-Id", required = false) Long associationId, Authentication authentication) {
        if (dto == null) {
            throw new SuiteException(400, "INVALID_TRANSACTION", "Movimento richiesto.");
        }
        access.requireRecord(authentication, dto.associationId(), associationId);
        Transaction entity = TransactionMapper.toEntity(dto);
        Transaction saved = financialService.saveTransaction(entity);
        return TransactionMapper.toDTO(saved);
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<TransactionDTO> updateTransaction(@PathVariable Long id, @RequestBody TransactionCreateDTO dto,
            @RequestHeader(value = "X-Association-Id", required = false) Long associationId, Authentication authentication) {
        if (dto == null) {
            throw new SuiteException(400, "INVALID_TRANSACTION", "Movimento richiesto.");
        }
        Transaction actual = financialService.getTransactionById(id)
                .orElseThrow(() -> new SuiteException(404, "TRANSACTION_MISSING", "Movimento non trovato."));
        access.requireRecord(authentication, actual.getAssociationId(), associationId);
        if (dto.associationId() != null && !dto.associationId().equals(actual.getAssociationId())) {
            throw new SuiteException(403, "ASSOCIATION_IMMUTABLE", "Non è possibile spostare il movimento in un'altra associazione.");
        }
        Transaction updated = financialService.updateTransaction(id, TransactionMapper.toEntity(dto));
        return ResponseEntity.ok(TransactionMapper.toDTO(updated));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id,
            @RequestHeader(value = "X-Association-Id", required = false) Long associationId, Authentication authentication) {
        Transaction actual = financialService.getTransactionById(id)
                .orElseThrow(() -> new SuiteException(404, "TRANSACTION_MISSING", "Movimento non trovato."));
        access.requireRecord(authentication, actual.getAssociationId(), associationId);
        financialService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transactions")
    public List<TransactionDTO> getAllTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Long membershipId,
            @RequestParam(required = false) Long inventoryItemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "X-Association-Id", required = false) Long selectedAssociation, Authentication authentication
    ) {
        Long associationId = access.readAssociation(authentication, selectedAssociation, null);
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (associationId != null) {
                predicates.add(cb.equal(root.get("associationId"), associationId));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            if (activityId != null) {
                predicates.add(cb.equal(root.get("activityId"), activityId));
            }
            if (membershipId != null) {
                predicates.add(cb.equal(root.get("membershipId"), membershipId));
            }
            if (inventoryItemId != null) {
                predicates.add(cb.equal(root.get("inventoryItemId"), inventoryItemId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return financialService.getTransactions(spec).stream()
                .map(TransactionMapper::toDTO)
                .toList();
    }

    @GetMapping("/journal-entries")
    public List<JournalEntry> getJournalEntries(
            @RequestParam(required = false) Long associationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "X-Association-Id", required = false) Long selectedAssociation, Authentication authentication
    ) {
        Long scope = access.readAssociation(authentication, selectedAssociation, associationId);
        return financialService.getJournalEntries(scope, startDate, endDate);
    }

    @GetMapping("/yoy-comparison")
    public ResponseEntity<Map<String, BigDecimal>> getYoyComparison(@RequestParam(defaultValue = "2024") int year,
            @RequestHeader(value = "X-Association-Id", required = false) Long associationId, Authentication authentication) {
        Long scope = access.readAssociation(authentication, associationId, null);
        return ResponseEntity.ok(financialService.getYearOverYearComparison(year, scope));
    }

    @GetMapping("/comparison")
    public ResponseEntity<Map<String, BigDecimal>> getCustomComparison(
            @RequestParam int year1,
            @RequestParam int year2,
            @RequestHeader(value = "X-Association-Id", required = false) Long associationId, Authentication authentication
    ) {
        Long scope = access.readAssociation(authentication, associationId, null);
        return ResponseEntity.ok(financialService.getCustomComparison(year1, year2, scope));
    }
}
