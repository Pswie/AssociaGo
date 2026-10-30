package com.associago.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActivityParticipantDTO(Long id, Long activityId, UserName user,
        LocalDate registrationDate, LocalDate expirationDate, Boolean paid, BigDecimal amountPaid,
        Long invoiceId, String paymentMethod, LocalDate paymentDate, String notes, boolean active,
        LocalDate cancellationDate, String cancellationReason, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record UserName(Long id, String firstName, String lastName) {}
}
