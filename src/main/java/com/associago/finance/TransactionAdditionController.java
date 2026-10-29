package com.associago.finance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le aggiunte su un movimento: IVA, commissione, spese, altro.
 *
 * <p>Il totale del movimento non si scrive mai da qui: lo ricalcola il servizio
 * a ogni aggiunta o rimozione. Chi chiama dichiara quanto aggiungere, non quanto
 * deve venire.
 */
@RestController
@RequestMapping("/api/v1/transactions/{transactionId}/additions")
public class TransactionAdditionController {

    private final TransactionAdditionService service;

    public TransactionAdditionController(TransactionAdditionService service) {
        this.service = service;
    }

    /** Con segno: negativo per correggere in diminuzione. */
    public record AdditionRequest(String kind, BigDecimal amount, String description, Long userId) {}

    @GetMapping
    public List<TransactionAddition> list(@PathVariable Long transactionId) {
        return service.list(transactionId);
    }

    @PostMapping
    public ResponseEntity<TransactionAddition> add(@PathVariable Long transactionId,
                                                   @RequestBody AdditionRequest body) {
        try {
            return ResponseEntity.ok(service.add(
                    transactionId, body.kind(), body.amount(), body.description(), body.userId()));
        } catch (IllegalArgumentException errore) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{additionId}")
    public ResponseEntity<Void> remove(@PathVariable Long transactionId,
                                       @PathVariable Long additionId) {
        service.remove(additionId);
        return ResponseEntity.noContent().build();
    }
}
