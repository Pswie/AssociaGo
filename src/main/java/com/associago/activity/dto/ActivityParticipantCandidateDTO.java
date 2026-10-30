package com.associago.activity.dto;

public record ActivityParticipantCandidateDTO(Long userId, Long membershipId, String firstName,
                                               String lastName, String membershipCardNumber, String status) {}
