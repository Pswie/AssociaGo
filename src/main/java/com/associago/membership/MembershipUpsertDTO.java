package com.associago.membership;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDate;

/** Nested anagraphic bodies are deliberately not accepted beyond their IDs. */
public record MembershipUpsertDTO(Long id, Reference user, Reference association, String membershipCardNumber,
        String role, String status, LocalDate joinDate, LocalDate expirationDate, LocalDate lastRenewalDate,
        String notes, @JsonAlias("isBoardMember") Boolean boardMember) {
    public record Reference(Long id) {}
}
