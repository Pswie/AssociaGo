package com.associago.csvimport;

import com.associago.association.Association;
import com.associago.association.repository.AssociationRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTests {
    @Mock private ImportLogRepository logRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAssociationRepository membershipRepository;
    @Mock private AssociationRepository associationRepository;
    private CsvImportService service;
    private Association association;

    @BeforeEach
    void setUp() {
        service = new CsvImportService(logRepository, userRepository, membershipRepository, associationRepository);
        association = new Association();
        association.setId(42L);
    }

    @Test
    void italianSemicolonCsvWithBomCreatesVisibleAssociationMembership() throws Exception {
        stubAssociationAndLogSave();
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });

        ImportLog log = service.importMembers(csv("\uFEFFnome;cognome;email;note\nMario;Rossi;mario@example.org;Nuovo socio"), 42L, null);

        assertThat(log.getImportedRows()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<UserAssociation> membership = ArgumentCaptor.forClass(UserAssociation.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getAssociation().getId()).isEqualTo(42L);
        assertThat(membership.getValue().getUser().getFirstName()).isEqualTo("Mario");
        assertThat(membership.getValue().getUser().getId()).isEqualTo(7L);
        assertThat(membership.getValue().getNotes()).isEqualTo("Nuovo socio");
    }

    @Test
    void existingUserInAnotherAssociationIsLinkedWithoutDuplicatingUser() throws Exception {
        stubAssociationAndLogSave();
        User user = new User();
        user.setId(7L);
        when(userRepository.findByEmailIgnoreCase("mario@example.org")).thenReturn(Optional.of(user));

        ImportLog log = service.importMembers(csv("first_name,last_name,email\nMario,Rossi,mario@example.org"), 42L, null);

        assertThat(log.getImportedRows()).isEqualTo(1);
        verify(userRepository, never()).save(any());
        verify(membershipRepository).save(any());
    }

    @Test
    void previewCountsDuplicateRowsWithinFile() throws Exception {
        stubAssociationAndLogSave();

        ImportLog log = service.preview(csv("nome;cognome;email\nMario;Rossi;mario@example.org\nMario;Rossi;MARIO@example.org"),
                42L, "MEMBER", null);

        assertThat(log.getImportedRows()).isEqualTo(1);
        assertThat(log.getSkippedRows()).isEqualTo(1);
        verify(userRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void confirmationWithoutOriginalFileDoesNotPretendImportCompleted() {
        ImportLog log = previewLog("checksum");
        when(logRepository.findById(3L)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirmImport(3L, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("original CSV");
        assertThat(log.getStatus()).isEqualTo("PREVIEW");
        verify(logRepository, never()).save(any());
    }

    @Test
    void confirmationRejectsChangedFile() {
        ImportLog log = previewLog("different-checksum");
        when(logRepository.findById(3L)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirmImport(3L, csv("nome;cognome\nMario;Rossi")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changed");
        assertThat(log.getStatus()).isEqualTo("PREVIEW");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void confirmedOriginalFileImportsRowsAndUpdatesSamePreviewLog() throws Exception {
        stubAssociationAndLogSave();
        MockMultipartFile file = csv("nome;cognome\nMario;Rossi");
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        ImportLog log = previewLog(checksum);
        when(logRepository.findById(3L)).thenReturn(Optional.of(log));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportLog result = service.confirmImport(3L, file);

        assertThat(result).isSameAs(log);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getImportedRows()).isEqualTo(1);
        verify(membershipRepository).save(any());
        verify(logRepository).save(log);
    }

    @Test
    void unsupportedParticipantImportReturnsClearFailureBeforePreview() {
        assertThatThrownBy(() -> service.preview(csv("nome,cognome\nMario,Rossi"), 42L, "EVENT_PARTICIPANT", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not yet available");
        verify(logRepository, never()).save(any());
    }

    private void stubAssociationAndLogSave() {
        when(associationRepository.findById(42L)).thenReturn(Optional.of(association));
        when(logRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ImportLog previewLog(String checksum) {
        ImportLog log = new ImportLog();
        log.setId(3L);
        log.setAssociationId(42L);
        log.setEntityType("MEMBER");
        log.setStatus("PREVIEW");
        log.setFileChecksum(checksum);
        return log;
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "members.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
