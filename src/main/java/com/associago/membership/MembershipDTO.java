package com.associago.membership;

import com.associago.association.dto.AssociationDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipDTO(Long id, MembershipUserDTO user, AssociationDTO association, String membershipCardNumber,
        String role, String status, LocalDate joinDate, LocalDate expirationDate, LocalDate lastRenewalDate,
        String notes, boolean boardMember, LocalDateTime createdAt, LocalDateTime updatedAt) {}
