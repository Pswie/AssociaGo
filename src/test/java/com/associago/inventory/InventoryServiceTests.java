package com.associago.inventory;

import com.associago.association.Association;
import com.associago.finance.FinancialService;
import com.associago.inventory.repository.InventoryItemRepository;
import com.associago.inventory.repository.InventoryLoanRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {
    @Mock private InventoryItemRepository itemRepository;
    @Mock private InventoryLoanRepository loanRepository;
    @Mock private FinancialService financialService;
    @Mock private UserAssociationRepository membershipRepository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(itemRepository, loanRepository, financialService, membershipRepository);
    }

    @Test
    void lendingTheLastAvailableUnitMarksItemAsLoaned() {
        InventoryItem item = availableItem(1);
        stubLoanContext(item, 42L);
        InventoryLoan loan = newLoan();
        when(loanRepository.save(loan)).thenReturn(loan);

        service.createLoan(loan);

        assertThat(item.getStatus()).isEqualTo("LOANED");
        assertThat(loan.getStatus()).isEqualTo("ACTIVE");
        verify(itemRepository).save(item);
    }

    @Test
    void rejectsLendingWhenAllUnitsAreAlreadyOutstanding() {
        InventoryItem item = availableItem(1);
        stubLoanContext(item, 42L);
        InventoryLoan outstanding = newLoan();
        outstanding.setStatus("OVERDUE");
        when(loanRepository.findByItemId(1L)).thenReturn(List.of(outstanding));

        assertThatThrownBy(() -> service.createLoan(newLoan())).isInstanceOf(IllegalStateException.class);
        verify(loanRepository, never()).save(any());
    }

    @Test
    void rejectsBorrowerFromAnotherAssociation() {
        stubLoanContext(availableItem(1), 99L);

        assertThatThrownBy(() -> service.createLoan(newLoan())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("association");
        verify(loanRepository, never()).save(any());
    }

    @Test
    void returningLoanRestoresAvailability() {
        InventoryItem item = availableItem(1);
        item.setStatus("LOANED");
        InventoryLoan loan = newLoan();
        loan.setStatus("ACTIVE");
        loan.setLoanDate(LocalDateTime.now().minusDays(1));
        when(loanRepository.findById(3L)).thenReturn(Optional.of(loan));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(loanRepository.save(loan)).thenReturn(loan);

        service.returnLoan(3L, null);

        assertThat(loan.getStatus()).isEqualTo("RETURNED");
        assertThat(item.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void repeatingReturnPreservesOriginalReturnTime() {
        InventoryLoan loan = newLoan();
        loan.setStatus("RETURNED");
        LocalDateTime returnedAt = LocalDateTime.now().minusHours(2);
        loan.setActualReturnDate(returnedAt);
        when(loanRepository.findById(3L)).thenReturn(Optional.of(loan));

        service.returnLoan(3L, null);

        assertThat(loan.getActualReturnDate()).isEqualTo(returnedAt);
        verify(loanRepository, never()).save(any());
    }

    @Test
    void futurePurchaseDateCannotIncreaseDepreciatedValueAbovePurchasePrice() {
        InventoryItem item = availableItem(1);
        item.setPurchasePrice(new BigDecimal("100.00"));
        item.setPurchaseDate(LocalDate.now().plusYears(2));
        item.setDepreciationYears(5);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        assertThat(service.getItemById(1L).orElseThrow().getCurrentValue()).isEqualByComparingTo("100.00");
    }

    private InventoryItem availableItem(int quantity) {
        InventoryItem item = new InventoryItem();
        item.setId(1L);
        item.setAssociationId(42L);
        item.setQuantity(quantity);
        item.setStatus("AVAILABLE");
        return item;
    }

    private InventoryLoan newLoan() {
        InventoryLoan loan = new InventoryLoan();
        loan.setItemId(1L);
        loan.setUserAssociationId(2L);
        return loan;
    }

    private void stubLoanContext(InventoryItem item, Long associationId) {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        Association association = new Association();
        association.setId(associationId);
        UserAssociation membership = new UserAssociation();
        membership.setAssociation(association);
        when(membershipRepository.findById(2L)).thenReturn(Optional.of(membership));
    }
}
