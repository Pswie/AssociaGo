package com.associago.membership;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Only the anagraphic fields needed by membership management, never login material. */
public record MembershipUserDTO(Long id, String firstName, String lastName, String taxCode,
        String email, String phone, LocalDate birthDate, String birthPlace, String gender,
        String address, String city, String zipCode, LocalDateTime createdAt, LocalDateTime updatedAt) {}
