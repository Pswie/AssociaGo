package com.associago.attachment;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gli allegati, per qualunque cosa li abbia.
 *
 * <p>{@code ownerType} arriva dall'indirizzo e viene convertito con
 * {@link AttachmentOwner#from(String)}: un valore che non sia una voce
 * dell'enum si ferma qui con un 400, e non raggiunge mai la query. È l'unico
 * vincolo su un riferimento che il database non può garantire.
 */
@RestController
@RequestMapping("/api/v1/attachments/{ownerType}/{ownerId}")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    private static Map<String, Object> descrizione(Attachment allegato) {
        return Map.of(
                "id", allegato.getId(),
                "kind", allegato.getKind(),
                "fileName", allegato.getFileName(),
                "mimeType", allegato.getMimeType() == null ? "" : allegato.getMimeType(),
                "sizeBytes", allegato.getSizeBytes(),
                "sha256", allegato.getSha256());
    }

    /** L'elenco, senza i byte. */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String ownerType, @PathVariable Long ownerId) {
        AttachmentOwner owner = AttachmentOwner.from(ownerType);
        if (owner == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo non riconosciuto: " + ownerType));
        }
        List<Map<String, Object>> voci = service.list(owner, ownerId).stream()
                .map(AttachmentController::descrizione)
                .toList();
        return ResponseEntity.ok(voci);
    }

    @GetMapping("/{kind}/content")
    public ResponseEntity<byte[]> download(@PathVariable String ownerType,
                                           @PathVariable Long ownerId,
                                           @PathVariable String kind) {
        AttachmentOwner owner = AttachmentOwner.from(ownerType);
        if (owner == null) return ResponseEntity.badRequest().build();

        return service.get(owner, ownerId, kind)
                .map(allegato -> ResponseEntity.ok()
                        .contentType(allegato.getMimeType() == null
                                ? MediaType.APPLICATION_OCTET_STREAM
                                : MediaType.parseMediaType(allegato.getMimeType()))
                        // `attachment` e non `inline`: un PDF o un XML aperto
                        // dentro il browser eredita l'origine dell'applicazione,
                        // e un allegato arriva da fuori.
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''"
                                        + URLEncoder.encode(allegato.getFileName(), StandardCharsets.UTF_8))
                        .body(allegato.getContent()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{kind}")
    public ResponseEntity<?> upload(@PathVariable String ownerType,
                                    @PathVariable Long ownerId,
                                    @PathVariable String kind,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) Long userId) throws IOException {
        AttachmentOwner owner = AttachmentOwner.from(ownerType);
        if (owner == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo non riconosciuto: " + ownerType));
        }
        try {
            return ResponseEntity.ok(descrizione(service.upload(owner, ownerId, kind, file, userId)));
        } catch (IllegalArgumentException errore) {
            return ResponseEntity.badRequest().body(Map.of("message", errore.getMessage()));
        }
    }

    @DeleteMapping("/{kind}")
    public ResponseEntity<Void> delete(@PathVariable String ownerType,
                                       @PathVariable Long ownerId,
                                       @PathVariable String kind) {
        AttachmentOwner owner = AttachmentOwner.from(ownerType);
        if (owner == null) return ResponseEntity.badRequest().build();
        service.delete(owner, ownerId, kind);
        return ResponseEntity.noContent().build();
    }
}
