package com.associago.security.dto;

public record AuthRecoveryAssociationDTO(
        Long id,
        String name,
        String email,
        String type
) {}
