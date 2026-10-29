package com.associago.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Il certificato come esce dall'API.
 *
 * <p>Senza medico, struttura sanitaria, note e file: vedi
 * {@code MedicalCertificate} per il perché.
 */
public record MedicalCertificateDTO(
    Long id,
    Long memberId,
    Long associationId,
    String certificateType,
    LocalDate issueDate,
    LocalDate expiryDate,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
