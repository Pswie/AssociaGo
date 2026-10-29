package com.associago.csvimport;

import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CsvImportService {

    private final ImportLogRepository importLogRepository;
    private final UserRepository userRepository;
    private final UserAssociationRepository membershipRepository;
    private final AssociationRepository associationRepository;

    public CsvImportService(ImportLogRepository importLogRepository, UserRepository userRepository,
                            UserAssociationRepository membershipRepository, AssociationRepository associationRepository) {
        this.importLogRepository = importLogRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.associationRepository = associationRepository;
    }

    public List<ImportLog> getHistory(Long associationId) {
        return importLogRepository.findByAssociationIdOrderByCreatedAtDesc(associationId);
    }

    /**
     * Preview CSV content: validates, detects duplicates, returns preview data.
     */
    public ImportLog preview(MultipartFile file, Long associationId, String entityType, Long importedBy) throws IOException {
        requireMemberImport(entityType);
        requireAssociation(associationId);
        ImportLog log = new ImportLog();
        log.setAssociationId(associationId);
        log.setEntityType(entityType);
        log.setFileName(file.getOriginalFilename());
        log.setImportedBy(importedBy);
        log.setStatus("PREVIEW");
        log.setStartedAt(LocalDateTime.now());

        try {
            byte[] bytes = file.getBytes();
            log.setFileChecksum(sha256(bytes));

            List<String[]> rows = parseCsv(bytes);
            if (rows.isEmpty()) {
                log.setTotalRows(0);
                log.setErrorsDetail("[{\"row\":0,\"error\":\"File vuoto\"}]");
                log.setStatus("FAILED");
                return importLogRepository.save(log);
            }

            String[] header = rows.get(0);
            List<String[]> dataRows = rows.subList(1, rows.size());
            log.setTotalRows(dataRows.size());

            List<Map<String, String>> errors = new ArrayList<>();
            int valid = 0;
            int skipped = 0;
            Set<String> seen = new HashSet<>();

            for (int i = 0; i < dataRows.size(); i++) {
                String[] row = dataRows.get(i);
                Map<String, String> rowMap = mapRow(header, row);

                List<String> rowErrors = validateRow(rowMap, entityType);
                if (!rowErrors.isEmpty()) {
                    Map<String, String> err = new HashMap<>();
                    err.put("row", String.valueOf(i + 2)); // 1-indexed + header
                    err.put("error", String.join("; ", rowErrors));
                    errors.add(err);
                } else {
                    // Check for duplicates
                    if (isDuplicate(rowMap, associationId) || duplicateInFile(rowMap, seen)) {
                        skipped++;
                    } else {
                        valid++;
                    }
                }
            }

            log.setImportedRows(valid);
            log.setSkippedRows(skipped);
            log.setErrorRows(errors.size());
            if (dataRows.isEmpty() || (valid == 0 && !errors.isEmpty())) {
                log.setStatus("FAILED");
            }
            if (!errors.isEmpty()) {
                log.setErrorsDetail(errors.toString());
            }
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorsDetail("[{\"row\":0,\"error\":\"" + e.getMessage() + "\"}]");
        }

        return importLogRepository.save(log);
    }

    /**
     * Confirm import: actually persists the data.
     */
    @Transactional
    public ImportLog confirmImport(Long importLogId, MultipartFile file) throws IOException {
        ImportLog log = importLogRepository.findById(importLogId)
                .orElseThrow(() -> new RuntimeException("Import log not found: " + importLogId));

        if (!"PREVIEW".equals(log.getStatus())) {
            throw new IllegalStateException("Import can only be confirmed from PREVIEW status");
        }

        requireMemberImport(log.getEntityType());
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Upload the original CSV file to confirm this preview. No data has been imported.");
        }
        if (!Objects.equals(log.getFileChecksum(), sha256(file.getBytes()))) {
            throw new IllegalArgumentException("The CSV file has changed since the preview. Generate a new preview first.");
        }
        return importMemberRows(file, requireAssociation(log.getAssociationId()), log);
    }

    /**
     * Full import: preview + confirm in one step for API integrations.
     */
    @Transactional
    public ImportLog importMembers(MultipartFile file, Long associationId, Long importedBy) throws IOException {
        Association association = requireAssociation(associationId);
        ImportLog log = new ImportLog();
        log.setAssociationId(associationId);
        log.setEntityType("MEMBER");
        log.setFileName(file.getOriginalFilename());
        log.setImportedBy(importedBy);
        log.setStatus("COMPLETED");
        log.setStartedAt(LocalDateTime.now());

        return importMemberRows(file, association, log);
    }

    private ImportLog importMemberRows(MultipartFile file, Association association, ImportLog log) throws IOException {
        Long associationId = association.getId();
        log.setStatus("COMPLETED");
        log.setErrorsDetail(null);

        byte[] bytes = file.getBytes();
        log.setFileChecksum(sha256(bytes));

        List<String[]> rows = parseCsv(bytes);
        if (rows.size() < 2) {
            log.setTotalRows(0);
            log.setStatus("FAILED");
            return importLogRepository.save(log);
        }

        String[] header = rows.get(0);
        List<String[]> dataRows = rows.subList(1, rows.size());
        log.setTotalRows(dataRows.size());

        int imported = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorDetails = new ArrayList<>();

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            Map<String, String> rowMap = mapRow(header, row);

            List<String> rowErrors = validateRow(rowMap, "MEMBER");
            if (!rowErrors.isEmpty()) {
                errors++;
                errorDetails.add("Riga " + (i + 2) + ": " + String.join("; ", rowErrors));
                continue;
            }

            if (isDuplicate(rowMap, associationId) || duplicateInFile(rowMap, seen)) {
                skipped++;
                continue;
            }

            User user = findUser(rowMap).orElseGet(() -> {
                User member = new User();
                member.setFirstName(rowMap.getOrDefault("first_name", rowMap.get("nome")));
                member.setLastName(rowMap.getOrDefault("last_name", rowMap.get("cognome")));
                member.setEmail(rowMap.get("email"));
                member.setTaxCode(fiscalCode(rowMap));
                member.setPhone(rowMap.getOrDefault("phone", rowMap.get("telefono")));
                return userRepository.save(member);
            });
            UserAssociation membership = new UserAssociation();
            membership.setUser(user);
            membership.setAssociation(association);
            membership.setJoinDate(java.time.LocalDate.now());
            membership.setNotes(rowMap.getOrDefault("notes", rowMap.get("note")));
            membershipRepository.save(membership);
            imported++;
        }

        log.setImportedRows(imported);
        log.setSkippedRows(skipped);
        log.setErrorRows(errors);
        if (errors > 0 && imported == 0) {
            log.setStatus("FAILED");
        }
        if (!errorDetails.isEmpty()) {
            log.setErrorsDetail(String.join("\n", errorDetails));
        }
        log.setCompletedAt(LocalDateTime.now());

        return importLogRepository.save(log);
    }

    // --- Helpers ---

    private List<String[]> parseCsv(byte[] bytes) throws IOException {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        String header = content.split("\\R", 2)[0];
        char separator = header.chars().filter(c -> c == ';').count() > header.chars().filter(c -> c == ',').count() ? ';' : ',';
        try (CSVReader reader = new CSVReaderBuilder(new StringReader(content))
                .withCSVParser(new CSVParserBuilder().withSeparator(separator).build()).build()) {
            return reader.readAll();
        } catch (CsvException e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        }
    }

    private Map<String, String> mapRow(String[] header, String[] row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < header.length && i < row.length; i++) {
            String key = header[i].trim().toLowerCase(Locale.ROOT).replace(" ", "_");
            String value = row[i] != null ? row[i].trim() : null;
            if (value != null && !value.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<String> validateRow(Map<String, String> row, String entityType) {
        List<String> errors = new ArrayList<>();
        if ("MEMBER".equals(entityType)) {
            String firstName = row.getOrDefault("first_name", row.get("nome"));
            String lastName = row.getOrDefault("last_name", row.get("cognome"));
            if (firstName == null || firstName.isEmpty()) errors.add("Nome mancante");
            if (lastName == null || lastName.isEmpty()) errors.add("Cognome mancante");
        }
        return errors;
    }

    private boolean isDuplicate(Map<String, String> row, Long associationId) {
        return findUser(row).map(user -> membershipRepository
                .findByUserIdAndAssociationId(user.getId(), associationId).isPresent()).orElse(false);
    }

    private Optional<User> findUser(Map<String, String> row) {
        String fiscalCode = fiscalCode(row);
        if (fiscalCode != null) {
            Optional<User> byCode = userRepository.findByTaxCode(fiscalCode);
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        String email = row.get("email");
        return email == null || email.isBlank() ? Optional.empty() : userRepository.findByEmailIgnoreCase(email);
    }

    private String fiscalCode(Map<String, String> row) {
        String value = row.getOrDefault("fiscal_code", row.get("codice_fiscale"));
        return value == null || value.isBlank() ? null : value.toUpperCase(Locale.ROOT);
    }

    private boolean duplicateInFile(Map<String, String> row, Set<String> seen) {
        String fiscalCode = fiscalCode(row);
        String email = row.get("email");
        String codeKey = fiscalCode == null ? null : "code:" + fiscalCode;
        String emailKey = email == null || email.isBlank() ? null : "email:" + email.toLowerCase(Locale.ROOT);
        boolean duplicate = (codeKey != null && seen.contains(codeKey)) || (emailKey != null && seen.contains(emailKey));
        if (codeKey != null) seen.add(codeKey);
        if (emailKey != null) seen.add(emailKey);
        return duplicate;
    }

    private Association requireAssociation(Long associationId) {
        if (associationId == null) {
            throw new IllegalArgumentException("Association is required for member imports.");
        }
        return associationRepository.findById(associationId)
                .orElseThrow(() -> new IllegalArgumentException("Association not found."));
    }

    private void requireMemberImport(String entityType) {
        if (!"MEMBER".equals(entityType)) {
            throw new IllegalStateException("Activity and event participant CSV imports are not yet available. "
                    + "Add participants using the participant form.");
        }
    }

    private String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.HexFormat HexFormat = java.util.HexFormat.of();
}
