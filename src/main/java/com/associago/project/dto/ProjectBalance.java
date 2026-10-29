package com.associago.project.dto;

import com.associago.project.Project;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Rendiconto di un progetto: quanto era previsto, quanto è entrato, quanto è
 * uscito e come sta andando.
 *
 * @param verdict {@code avanzo}, {@code disavanzo} o {@code pareggio}
 * @param overBudget vero se le uscite hanno superato il budget dichiarato
 * @param overdue vero se la scadenza è passata e il progetto non è chiuso
 *
 * @since 0.2.0
 */
public record ProjectBalance(
        Long projectId,
        String name,
        String status,
        BigDecimal budget,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal saldo,
        String verdict,
        boolean overBudget,
        boolean overdue
) {

    public static ProjectBalance of(Project project,
                                    BigDecimal income,
                                    BigDecimal expense,
                                    LocalDate today) {
        BigDecimal safeIncome = income == null ? BigDecimal.ZERO : income;
        BigDecimal safeExpense = expense == null ? BigDecimal.ZERO : expense;
        BigDecimal saldo = safeIncome.subtract(safeExpense);

        String verdict = switch (Integer.signum(saldo.signum())) {
            case 1 -> "avanzo";
            case -1 -> "disavanzo";
            default -> "pareggio";
        };

        // Senza budget dichiarato non c'è nulla da sforare: un progetto senza
        // previsione non è "sopra budget", è solo non pianificato.
        boolean overBudget = project.getBudget() != null
                && safeExpense.compareTo(project.getBudget()) > 0;

        return new ProjectBalance(
                project.getId(),
                project.getName(),
                project.getStatus(),
                project.getBudget(),
                safeIncome,
                safeExpense,
                saldo,
                verdict,
                overBudget,
                project.isOverdue(today));
    }
}
