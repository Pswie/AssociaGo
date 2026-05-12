package com.associago.security.dto;

public record PasswordResetRequest(
        Long associationId,
        String fiscalCode,
        String newPassword
) {}
