package com.associago.volunteer;

import com.associago.association.repository.AssociationRepository;
import com.associago.attachment.Attachment;
import com.associago.attachment.AttachmentOwner;
import com.associago.attachment.AttachmentService;
import com.associago.finance.FinancialService;
import com.associago.finance.Transaction;
import com.associago.volunteer.repository.VolunteerExpenseRepository;
import com.associago.volunteer.repository.VolunteerRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolunteerReimbursementTests {
    @Mock private VolunteerRepository volunteerRepository;
    @Mock private VolunteerShiftRepository shiftRepository;
    @Mock private VolunteerExpenseRepository expenseRepository;
    @Mock private FinancialService financialService;
    @Mock private AssociationRepository associationRepository;
    @Mock private AttachmentService attachments;
    private VolunteerService service;
    private VolunteerExpense expense;

    @BeforeEach
    void setUp() {
        service = new VolunteerService(volunteerRepository, shiftRepository, expenseRepository,
                financialService, attachments, associationRepository);
        expense = new VolunteerExpense();
        expense.setId(7L);
        expense.setStatus("APPROVED");
        expense.setAmount(new BigDecimal("24.50"));
        expense.setDescription("Travel");
        expense.setExpenseDate(LocalDate.now());
        when(expenseRepository.findById(7L)).thenReturn(Optional.of(expense));
    }

    @Test
    void reimbursementUsesExplicitAssociationRatherThanAssociationOne() {
        when(associationRepository.existsById(42L)).thenReturn(true);
        when(expenseRepository.save(expense)).thenReturn(expense);
        when(attachments.exists(AttachmentOwner.VOLUNTEER_EXPENSE, 7L, Attachment.RICEVUTA)).thenReturn(true);
        Transaction saved = new Transaction();
        saved.setId(100L);
        when(financialService.saveTransaction(any())).thenReturn(saved);

        service.updateExpenseStatus(7L, "PAID", 42L);

        ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
        verify(financialService).saveTransaction(transaction.capture());
        assertThat(transaction.getValue().getAssociationId()).isEqualTo(42L);
        assertThat(expense.getTransactionId()).isEqualTo(100L);
    }

    @Test
    void paymentWithoutAssociationIsRejectedWithoutWritingTransaction() {
        assertThatThrownBy(() -> service.updateExpenseStatus(7L, "PAID", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(financialService, never()).saveTransaction(any());
        assertThat(expense.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void paidReimbursementCannotBeUnpaidWithoutReversingItsTransaction() {
        expense.setStatus("PAID");
        expense.setTransactionId(100L);

        assertThatThrownBy(() -> service.updateExpenseStatus(7L, "REJECTED", 42L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(expense.getStatus()).isEqualTo("PAID");
    }

    @Test
    void repeatedPaymentDoesNotCreateAnotherTransaction() {
        expense.setStatus("PAID");
        expense.setTransactionId(100L);

        service.updateExpenseStatus(7L, "PAID", 42L);

        verify(financialService, never()).saveTransaction(any());
    }

    @Test
    void existingAssociationCannotBeReplacedByCaller() {
        expense.setAssociationId(99L);

        assertThatThrownBy(() -> service.updateExpenseStatus(7L, "PAID", 42L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another association");
        verify(financialService, never()).saveTransaction(any());
    }
}
