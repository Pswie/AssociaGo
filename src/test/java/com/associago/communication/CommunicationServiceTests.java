package com.associago.communication;

import com.associago.association.Association;
import com.associago.association.AssociationService;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunicationServiceTests {
    @Mock private CommunicationRepository communicationRepository;
    @Mock private CommunicationTemplateRepository templateRepository;
    @Mock private CommunicationRecipientRepository recipientRepository;
    @Mock private UserAssociationRepository membershipRepository;
    @Mock private AssociationService associationService;
    @Mock private JavaMailSender mailSender;
    private CommunicationService service;
    private Communication communication;

    @BeforeEach
    void setUp() {
        service = new CommunicationService(communicationRepository, templateRepository, recipientRepository,
                membershipRepository, associationService, mailSender, new ObjectMapper());
        communication = new Communication();
        communication.setId(10L);
        communication.setAssociationId(42L);
        communication.setSubject("News");
        communication.setBodyText("Hello {{name}}");
        when(communicationRepository.findById(10L)).thenReturn(Optional.of(communication));
    }

    @Test
    void resolvesOnlyAssociationMembershipsWithExactStatusAndUniqueAddresses() {
        communication.setSegmentFilter("inactive");
        when(membershipRepository.findByAssociationId(42L)).thenReturn(List.of(
                membership(1L, "active@example.org", "ACTIVE", "MEMBER"),
                membership(2L, " inactive@example.org ", "INACTIVE", "MEMBER"),
                membership(3L, "INACTIVE@example.org", "INACTIVE", "MEMBER")));

        List<CommunicationRecipient> recipients = service.resolveRecipients(10L);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.getFirst().getUserId()).isEqualTo(2L);
        assertThat(recipients.getFirst().getEmail()).isEqualTo("inactive@example.org");
        verify(membershipRepository).findByAssociationId(42L);
    }

    @Test
    void supportsStructuredStatusAndRoleFilter() {
        communication.setSegmentFilter("{\"status\":[\"ACTIVE\"],\"roles\":[\"ADMIN\"],\"tags\":[]}");
        when(membershipRepository.findByAssociationId(42L)).thenReturn(List.of(
                membership(1L, "admin@example.org", "ACTIVE", "ADMIN"),
                membership(2L, "member@example.org", "ACTIVE", "MEMBER"),
                membership(3L, "expired@example.org", "EXPIRED", "ADMIN")));

        assertThat(service.resolveRecipients(10L)).extracting(CommunicationRecipient::getEmail)
                .containsExactly("admin@example.org");
    }

    @Test
    void rejectsUnknownFiltersInsteadOfSendingToEveryone() {
        communication.setSegmentFilter("not-a-status");

        assertThatThrownBy(() -> service.resolveRecipients(10L)).isInstanceOf(IllegalArgumentException.class);
        verify(recipientRepository, never()).saveAll(any());
    }

    @Test
    void repeatedResolutionUsesExistingRecipients() {
        CommunicationRecipient existing = recipient("existing@example.org", "PENDING");
        when(recipientRepository.findByCommunicationId(10L)).thenReturn(List.of(existing));

        assertThat(service.resolveRecipients(10L)).containsExactly(existing);
        verify(membershipRepository, never()).findByAssociationId(any());
        verify(recipientRepository, never()).saveAll(any());
    }

    @Test
    void failedSendsAreReportedAsFailedAndCanBeRetriedWithoutResendingSuccesses() {
        communication.setStatus("FAILED");
        CommunicationRecipient sent = recipient("sent@example.org", "SENT");
        CommunicationRecipient retry = recipient("retry@example.org", "FAILED");
        retry.setErrorMessage("Old failure");
        when(recipientRepository.findByCommunicationId(10L)).thenReturn(List.of(sent, retry));
        when(associationService.findById(42L)).thenReturn(Optional.of(new Association()));
        when(communicationRepository.save(communication)).thenReturn(communication);

        service.send(10L, null);

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(communication.getDeliveredCount()).isEqualTo(2);
        assertThat(communication.getFailedCount()).isZero();
        assertThat(communication.getStatus()).isEqualTo("SENT");
        assertThat(retry.getErrorMessage()).isNull();
    }

    @Test
    void smtpFailureDoesNotClaimTheCommunicationWasSent() {
        when(recipientRepository.findByCommunicationId(10L)).thenReturn(List.of(recipient("bad@example.org", "PENDING")));
        when(associationService.findById(42L)).thenReturn(Optional.of(new Association()));
        when(communicationRepository.save(communication)).thenReturn(communication);
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        service.send(10L, null);

        assertThat(communication.getStatus()).isEqualTo("FAILED");
        assertThat(communication.getFailedCount()).isEqualTo(1);
        assertThat(communication.getSentAt()).isNull();
    }

    @Test
    void sentCommunicationIsNotSentAgain() {
        communication.setStatus("SENT");

        assertThat(service.send(10L, null)).isSameAs(communication);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void emptyAudienceDoesNotClaimSuccess() {
        assertThatThrownBy(() -> service.send(10L, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No recipients");
        assertThat(communication.getStatus()).isEqualTo("DRAFT");
    }

    private UserAssociation membership(Long userId, String email, String status, String role) {
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        UserAssociation membership = new UserAssociation();
        membership.setUser(user);
        membership.setStatus(status);
        membership.setRole(role);
        return membership;
    }

    private CommunicationRecipient recipient(String email, String status) {
        CommunicationRecipient recipient = new CommunicationRecipient();
        recipient.setCommunicationId(10L);
        recipient.setEmail(email);
        recipient.setStatus(status);
        return recipient;
    }
}
