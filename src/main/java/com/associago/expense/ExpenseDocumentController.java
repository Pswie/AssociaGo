package com.associago.expense;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API dei documenti di spesa: fatture ricevute, ricevute, note.
 *
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/expense-documents")
public class ExpenseDocumentController {

    private final ExpenseDocumentService service;

    public ExpenseDocumentController(ExpenseDocumentService service) {
        this.service = service;
    }

    /**
     * @param anno l'esercizio da guardare; assente significa «tutte». Una
     *             fattura appartiene all'anno della sua data.
     */
    @GetMapping
    public List<ExpenseDocument> list(@RequestParam Long associationId,
                                      @RequestParam(required = false) Integer anno) {
        return service.findByAssociation(associationId, anno);
    }

    @GetMapping("/overdue")
    public List<ExpenseDocument> overdue(@RequestParam Long associationId) {
        return service.overdue(associationId, LocalDate.now());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseDocument> byId(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Import di una fattura elettronica in formato XML.
     *
     * <p>Risponde {@code 200} con {@code alreadyPresent: true} quando il file
     * era già stato importato: non è un errore, è la difesa contro il doppione
     * in prima nota.
     */
    @PostMapping(value = "/import-xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importXml(@RequestParam("file") MultipartFile file,
                                       @RequestParam Long associationId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nessun file caricato."));
        }
        try {
            ExpenseDocumentService.ImportResult result =
                    service.importFattura(file.getBytes(), associationId);
            return ResponseEntity.ok(Map.of(
                    "document", result.document(),
                    "alreadyPresent", result.alreadyPresent(),
                    "message", result.alreadyPresent()
                            ? "Questa fattura era già stata importata: nessun duplicato creato."
                            : "Fattura importata."));
        } catch (FatturaXmlParser.InvalidFatturaException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Impossibile leggere il file caricato."));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ExpenseDocument document) {
        try {
            return ResponseEntity.ok(service.save(document));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ExpenseDocument details) {
        try {
            return service.update(id, details)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
