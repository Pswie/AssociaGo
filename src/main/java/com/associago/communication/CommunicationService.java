package com.associago.communication;

import com.associago.association.Association;
import com.associago.association.AssociationService;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class CommunicationService {

    private final CommunicationRepository communicationRepository;
    private final CommunicationTemplateRepository templateRepository;
    private final CommunicationRecipientRepository recipientRepository;
    private final UserAssociationRepository membershipRepository;
    private final AssociationService associationService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    public CommunicationService(CommunicationRepository communicationRepository,
                                CommunicationTemplateRepository templateRepository,
                                CommunicationRecipientRepository recipientRepository,
                                UserAssociationRepository membershipRepository,
                                AssociationService associationService,
                                JavaMailSender mailSender,
                                ObjectMapper objectMapper) {
        this.communicationRepository = communicationRepository;
        this.templateRepository = templateRepository;
        this.recipientRepository = recipientRepository;
        this.membershipRepository = membershipRepository;
        this.associationService = associationService;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
    }

    // --- Templates ---

    public List<CommunicationTemplate> getTemplates(Long associationId) {
        return templateRepository.findByAssociationId(associationId);
    }

    @Transactional
    public CommunicationTemplate saveTemplate(CommunicationTemplate template) {
        template.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    // --- Communications ---

    public List<Communication> getCommunications(Long associationId) {
        return communicationRepository.findByAssociationIdOrderByCreatedAtDesc(associationId);
    }

    public Communication getById(Long id) {
        return communicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Communication not found: " + id));
    }

    @Transactional
    public Communication create(Communication communication) {
        communication.setId(null);
        communication.setStatus("DRAFT");
        communication.setTotalRecipients(0);
        communication.setDeliveredCount(0);
        communication.setFailedCount(0);
        communication.setSentAt(null);
        communication.setSentBy(null);
        recipientFilter(communication.getSegmentFilter());
        communication.setUpdatedAt(LocalDateTime.now());
        return communicationRepository.save(communication);
    }

    @Transactional
    public Communication update(Long id, Communication details) {
        Communication comm = getById(id);
        if (!"DRAFT".equals(comm.getStatus())) {
            throw new IllegalStateException("Only draft communications can be edited.");
        }
        recipientFilter(details.getSegmentFilter());
        if (!Objects.equals(comm.getSegmentFilter(), details.getSegmentFilter())) {
            recipientRepository.deleteByCommunicationId(id);
            comm.setTotalRecipients(0);
        }
        comm.setSubject(details.getSubject());
        comm.setBodyHtml(details.getBodyHtml());
        comm.setBodyText(details.getBodyText());
        comm.setSenderEmail(details.getSenderEmail());
        comm.setSenderName(details.getSenderName());
        comm.setSegmentFilter(details.getSegmentFilter());
        comm.setNotes(details.getNotes());
        comm.setUpdatedAt(LocalDateTime.now());
        return communicationRepository.save(comm);
    }

    @Transactional
    public void delete(Long id) {
        communicationRepository.deleteById(id);
    }

    /**
     * Resolves recipients from segment filter and persists them.
     */
    @Transactional
    public List<CommunicationRecipient> resolveRecipients(Long communicationId) {
        Communication comm = getById(communicationId);
        List<CommunicationRecipient> existing = recipientRepository.findByCommunicationId(communicationId);
        if (!existing.isEmpty()) {
            return existing;
        }
        if (!"DRAFT".equals(comm.getStatus())) {
            throw new IllegalStateException("Recipients can only be resolved for a draft communication.");
        }
        if (comm.getAssociationId() == null) {
            throw new IllegalArgumentException("Association is required to resolve recipients.");
        }
        List<CommunicationRecipient> recipients = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        Predicate<UserAssociation> filter = recipientFilter(comm.getSegmentFilter());

        for (UserAssociation membership : membershipRepository.findByAssociationId(comm.getAssociationId())) {
            User member = membership.getUser();
            if (member != null && member.getEmail() != null && !member.getEmail().isBlank()) {
                String email = member.getEmail().trim();
                if (filter.test(membership) && emails.add(email.toLowerCase(Locale.ROOT))) {
                    CommunicationRecipient recipient = new CommunicationRecipient();
                    recipient.setCommunicationId(communicationId);
                    recipient.setUserId(member.getId());
                    recipient.setEmail(email);
                    recipient.setName(member.getFirstName() + " " + member.getLastName());
                    recipients.add(recipient);
                }
            }
        }

        recipientRepository.saveAll(recipients);
        comm.setTotalRecipients(recipients.size());
        communicationRepository.save(comm);
        return recipients;
    }

    /**
     * Sends the communication to all resolved recipients via SMTP.
     */
    @Transactional
    public Communication send(Long communicationId, Long sentBy) {
        Communication comm = getById(communicationId);
        if ("SENT".equals(comm.getStatus())) {
            return comm;
        }
        if (!"DRAFT".equals(comm.getStatus()) && !"FAILED".equals(comm.getStatus())) {
            throw new IllegalStateException("Communication is already being sent.");
        }
        List<CommunicationRecipient> recipients = recipientRepository.findByCommunicationId(communicationId);

        if (recipients.isEmpty()) {
            recipients = resolveRecipients(communicationId);
        }
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No recipients match this communication. Check memberships and email addresses.");
        }

        comm.setStatus("SENDING");
        comm.setSentBy(sentBy);
        communicationRepository.save(comm);

        int delivered = 0;
        int failed = 0;

        Association association = associationService.findById(comm.getAssociationId()).orElse(new Association());

        for (CommunicationRecipient recipient : recipients) {
            if ("SENT".equals(recipient.getStatus()) || "DELIVERED".equals(recipient.getStatus())) {
                delivered++;
                continue;
            }
            try {
                String personalizedBody = mergeBody(comm.getBodyText() != null ? comm.getBodyText() : comm.getBodyHtml(),
                        recipient.getName(), association.getName());

                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(comm.getSenderEmail() != null ? comm.getSenderEmail() : "noreply@associago.local");
                message.setTo(recipient.getEmail());
                message.setSubject(comm.getSubject());
                message.setText(personalizedBody.replaceAll("<[^>]*>", "")); // Strip HTML for plain text

                mailSender.send(message);
                recipient.setStatus("SENT");
                recipient.setSentAt(LocalDateTime.now());
                recipient.setErrorMessage(null);
                delivered++;
            } catch (Exception e) {
                recipient.setStatus("FAILED");
                recipient.setErrorMessage(e.getMessage());
                failed++;
            }
        }

        recipientRepository.saveAll(recipients);
        comm.setDeliveredCount(delivered);
        comm.setFailedCount(failed);
        comm.setTotalRecipients(recipients.size());
        comm.setStatus(failed == 0 ? "SENT" : "FAILED");
        if (failed == 0) {
            comm.setSentAt(LocalDateTime.now());
        }
        comm.setUpdatedAt(LocalDateTime.now());
        return communicationRepository.save(comm);
    }

    public List<CommunicationRecipient> getRecipients(Long communicationId) {
        return recipientRepository.findByCommunicationId(communicationId);
    }

    // --- Helpers ---

    private Predicate<UserAssociation> recipientFilter(String value) {
        if (value == null || value.isBlank()) {
            return membership -> true;
        }
        String filter = value.trim();
        if (!filter.startsWith("{")) {
            String status = filter.toUpperCase(Locale.ROOT);
            if (!Set.of("ACTIVE", "INACTIVE", "EXPIRED", "PENDING", "SUSPENDED", "RESIGNED").contains(status)) {
                throw new IllegalArgumentException("Use a membership status or a JSON filter with status and roles.");
            }
            return membership -> status.equalsIgnoreCase(membership.getStatus());
        }
        try {
            JsonNode json = objectMapper.readTree(filter);
            if (!json.isObject()) {
                throw new IllegalArgumentException("Recipient filter must be a JSON object.");
            }
            var fields = json.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!Set.of("status", "roles").contains(field)) {
                    if ("tags".equals(field) && json.get(field).isArray() && json.get(field).isEmpty()) {
                        continue;
                    }
                    throw new IllegalArgumentException("Unsupported recipient filter: " + field);
                }
            }
            Set<String> statuses = filterValues(json.get("status"));
            Set<String> roles = filterValues(json.get("roles"));
            return membership -> (statuses.isEmpty() || statuses.contains(normalize(membership.getStatus())))
                    && (roles.isEmpty() || roles.contains(normalize(membership.getRole())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Recipient filter contains invalid JSON.", e);
        }
    }

    private Set<String> filterValues(JsonNode node) {
        Set<String> values = new HashSet<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            values.add(normalize(node.asText()));
            return values;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Status and roles filters must be strings or arrays of strings.");
        }
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isBlank()) {
                throw new IllegalArgumentException("Recipient filter values must be non-empty strings.");
            }
            values.add(normalize(element.asText()));
        }
        return values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String mergeBody(String body, String recipientName, String associationName) {
        if (body == null) return "";
        body = body.replace("{{name}}", recipientName != null ? recipientName : "");
        body = body.replace("{{associationName}}", associationName != null ? associationName : "");
        return body;
    }
}
