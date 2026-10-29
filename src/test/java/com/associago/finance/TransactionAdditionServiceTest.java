package com.associago.finance;

import com.associago.finance.repository.TransactionAdditionRepository;
import com.associago.finance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * L'invariante delle uscite: {@code amount = baseAmount + somma delle aggiunte}.
 *
 * <p>È aritmetica da tre righe, e proprio per questo va provata: è quella che
 * decide che cifra finisce nel rendiconto. Un errore qui non si vede
 * nell'interfaccia — si vede a fine anno, quando i conti non tornano e nessuno
 * sa da quando.
 */
class TransactionAdditionServiceTest {

    private TransactionAdditionRepository additions;
    private TransactionRepository transactions;
    private TransactionAdditionService service;

    @BeforeEach
    void setUp() {
        additions = mock(TransactionAdditionRepository.class);
        transactions = mock(TransactionRepository.class);
        service = new TransactionAdditionService(additions, transactions);
        // save restituisce quel che riceve: qui interessa cosa viene scritto,
        // non cosa il database inventerebbe.
        when(transactions.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(additions.save(any(TransactionAddition.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Transaction movimento(BigDecimal amount, BigDecimal baseAmount) {
        Transaction t = new Transaction();
        t.setId(1L);
        t.setAmount(amount);
        t.setBaseAmount(baseAmount);
        t.setType(TransactionType.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("Un buono da 10 con 0,50 di IVA diventa un'uscita da 10,50")
    void ivaAlzaIlTotale() {
        Transaction buono = movimento(new BigDecimal("10.00"), new BigDecimal("10.00"));
        when(additions.sumByTransactionId(1L)).thenReturn(new BigDecimal("0.50"));

        service.add(1L, TransactionAddition.IVA, new BigDecimal("0.50"), "IVA 5%", 7L);

        assertThat(buono.getAmount()).isEqualByComparingTo("10.50");
        // Il nominale non si tocca: resta un buono da 10.
        assertThat(buono.getBaseAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Un'aggiunta negativa corregge in diminuzione senza riscrivere l'originale")
    void aggiuntaNegativaAbbassaIlTotale() {
        Transaction stima = movimento(new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(additions.sumByTransactionId(1L)).thenReturn(new BigDecimal("-15.00"));

        service.add(1L, TransactionAddition.ALTRO, new BigDecimal("-15.00"), "Rettifica", 7L);

        assertThat(stima.getAmount()).isEqualByComparingTo("85.00");
        assertThat(stima.getBaseAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Più aggiunte si sommano tutte")
    void piuAggiunteSiSommano() {
        Transaction spesa = movimento(new BigDecimal("200.00"), new BigDecimal("200.00"));
        when(additions.sumByTransactionId(1L)).thenReturn(new BigDecimal("46.40"));

        service.recompute(1L);

        assertThat(spesa.getAmount()).isEqualByComparingTo("246.40");
    }

    @Test
    @DisplayName("Una riga senza base assume che l'importo attuale sia la base")
    void rigaVecchiaSenzaBase() {
        // È il caso delle righe scritte prima che base_amount esistesse: non
        // hanno aggiunte, quindi partivano da dove sono arrivate.
        Transaction vecchia = movimento(new BigDecimal("75.00"), null);
        when(additions.sumByTransactionId(1L)).thenReturn(BigDecimal.ZERO);

        service.recompute(1L);

        assertThat(vecchia.getBaseAmount()).isEqualByComparingTo("75.00");
        assertThat(vecchia.getAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("Nessuna aggiunta: il totale resta la base")
    void sommaNullaNonRompe() {
        Transaction spesa = movimento(new BigDecimal("30.00"), new BigDecimal("30.00"));
        when(additions.sumByTransactionId(1L)).thenReturn(BigDecimal.ZERO);

        service.recompute(1L);

        assertThat(spesa.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("Un'aggiunta senza importo viene rifiutata")
    void importoObbligatorio() {
        movimento(new BigDecimal("10.00"), new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.add(1L, TransactionAddition.IVA, null, "vuota", 7L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(additions, never()).save(any());
    }

    @Test
    @DisplayName("Un'aggiunta su un movimento inesistente viene rifiutata")
    void movimentoInesistente() {
        when(transactions.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(99L, TransactionAddition.IVA, BigDecimal.ONE, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senza tipo, l'aggiunta è «altro» invece di restare vuota")
    void tipoPredefinito() {
        movimento(new BigDecimal("10.00"), new BigDecimal("10.00"));
        when(additions.sumByTransactionId(1L)).thenReturn(BigDecimal.ZERO);

        TransactionAddition salvata =
                service.add(1L, null, new BigDecimal("1.00"), null, 7L);

        assertThat(salvata.getKind()).isEqualTo(TransactionAddition.ALTRO);
    }
}
