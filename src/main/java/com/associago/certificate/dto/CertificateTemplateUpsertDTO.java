package com.associago.certificate.dto;

public record CertificateTemplateUpsertDTO(
        Long associationId,
        String name,
        String type,
        String bodyHtml,
        String mergeFields,
        String signatoryRoles,
        String orientation,
        String paperSize,
        Boolean active
) {}
