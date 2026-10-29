package com.associago.attachment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Il caricamento e lo scaricamento di qualunque allegato.
 *
 * <p>Un meccanismo solo per tutto ciò che prima era una colonna {@code *_path}.
 * Uno solo perché ventiquattro sarebbero stati ventiquattro limiti di
 * dimensione da ricordare, ventiquattro controlli di formato da scrivere e
 * ventiquattro posti dove dimenticarne uno.
 */
@Service
@Transactional
public class AttachmentService {

    /**
     * Quanto può pesare un allegato.
     *
     * <p>Dieci megabyte, molto meno dei cento che accetta il livello HTTP. Il
     * file attraversa il protocollo MySQL in un pacchetto solo e
     * {@code max_allowed_packet} su Aiven sta ben sotto: un file più grande
     * fallirebbe a metà scrittura con un errore che parla di pacchetti e non di
     * allegati. Una fattura scansionata sta in un paio di megabyte, un verbale
     * firmato pure.
     */
    public static final long DIMENSIONE_MASSIMA = 10L * 1024 * 1024;

    /**
     * Che cosa si accetta.
     *
     * <p>Elenco chiuso e non «tutto tranne»: una lista di cose vietate si aggira
     * sempre, una di cose permesse no.
     */
    private static final Set<String> TIPI_AMMESSI = Set.of(
            "application/pdf",
            "application/xml",
            "text/xml",
            "text/csv",
            "text/plain",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final AttachmentRepository repository;

    public AttachmentService(AttachmentRepository repository) {
        this.repository = repository;
    }

    public List<Attachment> list(AttachmentOwner ownerType, Long ownerId) {
        return repository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    public Optional<Attachment> get(AttachmentOwner ownerType, Long ownerId, String kind) {
        return repository.findByOwnerTypeAndOwnerIdAndKind(ownerType, ownerId, normalizza(kind));
    }

    /** Se c'è un allegato di quel tipo, senza leggerne i byte. */
    @Transactional(readOnly = true)
    public boolean exists(AttachmentOwner ownerType, Long ownerId, String kind) {
        return repository.existsByOwnerTypeAndOwnerIdAndKind(ownerType, ownerId, normalizza(kind));
    }

    /**
     * Carica un allegato, sostituendo quello dello stesso tipo se c'era.
     *
     * <p>Sostituzione e non accumulo: due versioni dello stesso statuto senza un
     * modo di dire quale valga sono peggio di una sola.
     */
    public Attachment upload(AttachmentOwner ownerType, Long ownerId, String kind,
                             MultipartFile file, Long userId) throws IOException {
        if (ownerType == null || ownerId == null) {
            throw new IllegalArgumentException("Indicare a che cosa si allega il file.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Nessun file ricevuto.");
        }
        if (file.getSize() > DIMENSIONE_MASSIMA) {
            throw new IllegalArgumentException(
                    "L'allegato supera i " + (DIMENSIONE_MASSIMA / 1024 / 1024) + " MB.");
        }
        String tipo = file.getContentType();
        if (tipo == null || !TIPI_AMMESSI.contains(tipo.toLowerCase())) {
            throw new IllegalArgumentException("Formato non ammesso: " + tipo);
        }

        byte[] contenuto = file.getBytes();
        String tipoAllegato = normalizza(kind);

        Attachment allegato = repository
                .findByOwnerTypeAndOwnerIdAndKind(ownerType, ownerId, tipoAllegato)
                .orElseGet(Attachment::new);
        allegato.setOwnerType(ownerType);
        allegato.setOwnerId(ownerId);
        allegato.setKind(tipoAllegato);
        allegato.setFileName(nomePulito(file.getOriginalFilename()));
        allegato.setMimeType(tipo);
        allegato.setSizeBytes(contenuto.length);
        allegato.setSha256(impronta(contenuto));
        allegato.setContent(contenuto);
        allegato.setCreatedBy(userId);

        return repository.save(allegato);
    }

    public void delete(AttachmentOwner ownerType, Long ownerId, String kind) {
        repository.findByOwnerTypeAndOwnerIdAndKind(ownerType, ownerId, normalizza(kind))
                .ifPresent(repository::delete);
    }

    /**
     * Toglie tutti gli allegati di qualcosa.
     *
     * <p>Va chiamata quando si cancella il proprietario: {@code owner_type} +
     * {@code owner_id} non è una chiave esterna, quindi il database non pulisce
     * da solo. È il prezzo dichiarato del meccanismo unico.
     */
    public void deleteAllOf(AttachmentOwner ownerType, Long ownerId) {
        repository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    private String normalizza(String kind) {
        return kind == null || kind.isBlank() ? Attachment.DOCUMENTO : kind.trim().toLowerCase();
    }

    /**
     * Il nome del file, ridotto a un nome.
     *
     * <p>Quello che arriva dal browser lo sceglie chi carica e può contenere
     * separatori di percorso. Non ci costruiamo sopra nessun percorso — il file
     * va nel database — ma il nome torna nell'intestazione di scaricamento, e
     * lì caratteri di controllo o virgolette sarebbero il modo di manometterla.
     */
    private String nomePulito(String originale) {
        if (originale == null || originale.isBlank()) return "allegato";
        String solaFoglia = originale.replace('\\', '/');
        solaFoglia = solaFoglia.substring(solaFoglia.lastIndexOf('/') + 1);
        String ripulito = solaFoglia.replaceAll("[\\p{Cntrl}\"\\r\\n]", "").trim();
        if (ripulito.isEmpty()) return "allegato";
        return ripulito.length() > 200 ? ripulito.substring(0, 200) : ripulito;
    }

    private String impronta(byte[] contenuto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenuto));
        } catch (NoSuchAlgorithmException impossibile) {
            throw new IllegalStateException(impossibile);
        }
    }
}
