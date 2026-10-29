package com.associago.project;

import com.associago.project.dto.ProjectBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il rendiconto di progetto, che è la ragione per cui il modulo esiste.
 */
@DisplayName("Rendiconto di progetto")
class ProjectBalanceTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 7, 24);

    private static Project progetto(String status, BigDecimal budget, LocalDate deadline) {
        Project project = new Project();
        project.setId(1L);
        project.setAssociationId(1L);
        project.setName("Doposcuola");
        project.setStatus(status);
        project.setBudget(budget);
        project.setDeadline(deadline);
        return project;
    }

    @Test
    @DisplayName("entrate maggiori delle uscite danno avanzo")
    void avanzo() {
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.ATTIVO, new BigDecimal("1000"), null),
                new BigDecimal("800"), new BigDecimal("500"), OGGI);

        assertThat(balance.saldo()).isEqualByComparingTo("300");
        assertThat(balance.verdict()).isEqualTo("avanzo");
        assertThat(balance.overBudget()).isFalse();
    }

    @Test
    @DisplayName("uscite maggiori delle entrate danno disavanzo")
    void disavanzo() {
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.ATTIVO, new BigDecimal("1000"), null),
                new BigDecimal("200"), new BigDecimal("500"), OGGI);

        assertThat(balance.saldo()).isEqualByComparingTo("-300");
        assertThat(balance.verdict()).isEqualTo("disavanzo");
    }

    @Test
    @DisplayName("nessun movimento significa pareggio, non errore")
    void nessunMovimento() {
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.PROPOSTO, null, null), null, null, OGGI);

        assertThat(balance.saldo()).isEqualByComparingTo("0");
        assertThat(balance.verdict()).isEqualTo("pareggio");
    }

    @Test
    @DisplayName("si sfora il budget sulle uscite, non sul saldo")
    void sforamentoBudget() {
        // Il punto: un progetto può avere saldo positivo grazie a un contributo
        // ricevuto e aver comunque speso più del previsto. Sono due domande
        // diverse e vanno tenute separate.
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.ATTIVO, new BigDecimal("1000"), null),
                new BigDecimal("2000"), new BigDecimal("1500"), OGGI);

        assertThat(balance.verdict()).isEqualTo("avanzo");
        assertThat(balance.overBudget()).isTrue();
    }

    @Test
    @DisplayName("senza budget dichiarato non si può sforare")
    void senzaBudgetNonSiSfora() {
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.ATTIVO, null, null),
                BigDecimal.ZERO, new BigDecimal("5000"), OGGI);

        assertThat(balance.overBudget()).isFalse();
    }

    @Test
    @DisplayName("un progetto attivo e scaduto è in ritardo")
    void inRitardo() {
        ProjectBalance balance = ProjectBalance.of(
                progetto(Project.ATTIVO, null, OGGI.minusDays(1)),
                BigDecimal.ZERO, BigDecimal.ZERO, OGGI);

        assertThat(balance.overdue()).isTrue();
    }

    @Test
    @DisplayName("un progetto completato o solo proposto non è mai in ritardo")
    void completatoOPropostoNonSonoInRitardo() {
        LocalDate scaduta = OGGI.minusDays(30);

        assertThat(ProjectBalance.of(progetto(Project.COMPLETATO, null, scaduta),
                null, null, OGGI).overdue()).isFalse();
        assertThat(ProjectBalance.of(progetto(Project.PROPOSTO, null, scaduta),
                null, null, OGGI).overdue()).isFalse();
    }
}
