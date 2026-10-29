package com.associago.member.dto;

import java.time.LocalDate;

/**
 * Quel che si può scrivere di un certificato.
 *
 * <p>I campi tolti (medico, struttura, note) restano fuori di proposito: se la
 * schermata desktop li invia ancora, Jackson li scarta invece di scriverli.
 */
public record MedicalCertificateUpsertDTO(
    Long memberId,
    Long associationId,
    String certificateType,
    LocalDate issueDate,
    LocalDate expiryDate
) {}
