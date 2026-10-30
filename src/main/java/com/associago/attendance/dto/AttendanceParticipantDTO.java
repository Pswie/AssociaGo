package com.associago.attendance.dto;

/** Separate namespaces: names describe an explicit reference and never establish identity. */
public record AttendanceParticipantDTO(String participantType, Long membershipId, Long memberId,
                                       String displayName, String membershipCardNumber, String status) {}
