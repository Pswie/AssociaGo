package com.associago.finance;

import com.associago.finance.repository.AccountRepository;
import com.associago.finance.repository.JournalEntryRepository;
import com.associago.finance.repository.PaymentMethodRepository;
import com.associago.finance.repository.TransactionRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.suite.SuiteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FinancialService {

    private final TransactionRepository transactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final UserAssociationRepository userAssociationRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private com.associago.project.repository.ProjectRepository projects;

    @Autowired
    public void setProjects(com.associago.project.repository.ProjectRepository projects) { this.projects = projects; }

    @Autowired
    public FinancialService(TransactionRepository transactionRepository,
                            PaymentMethodRepository paymentMethodRepository,
                            UserAssociationRepository userAssociationRepository,
                            AccountRepository accountRepository,
                            JournalEntryRepository journalEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.userAssociationRepository = userAssociationRepository;
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    @Transactional
    public Transaction saveTransaction(Transaction transaction) {
        validateProject(transaction);
        calculateCommissions(transaction);
        processMembershipRenewal(transaction);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Auto-generate Journal Entry for Double-Entry Bookkeeping
        createJournalEntryFromTransaction(savedTransaction);

        return savedTransaction;
    }

    @Transactional
    public Transaction updateTransaction(Long id, Transaction transactionDetails) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new SuiteException(404,"TRANSACTION_MISSING","Movimento non trovato."));
        if (transactionDetails.getAssociationId()!=null && !transactionDetails.getAssociationId().equals(transaction.getAssociationId()))
            throw new SuiteException(403,"ASSOCIATION_IMMUTABLE","Non è possibile spostare il movimento in un'altra associazione.");
        if (transaction.getProjectId()!=null && transactionDetails.getProjectId()!=null && !transaction.getProjectId().equals(transactionDetails.getProjectId()))
            throw new SuiteException(409,"TRANSACTION_LINKED","Il movimento è già attribuito a un altro progetto.");
        Long originalProject=transaction.getProjectId();
        Long requestedProject=originalProject!=null?originalProject:transactionDetails.getProjectId();
        if (requestedProject!=null) projects.lockForFinancialWrite(requestedProject);
        if (transactionRepository.lockIfProjectUnchanged(id,originalProject)!=1)
            throw new SuiteException(409,"TRANSACTION_CHANGED","Il movimento è stato attribuito da un'altra richiesta. Ricaricare prima di salvare.");
        transaction=transactionRepository.findById(id).orElseThrow(() -> new SuiteException(404,"TRANSACTION_MISSING","Movimento non trovato."));

        transaction.setDate(transactionDetails.getDate());
        transaction.setAmount(transactionDetails.getAmount());
        transaction.setType(transactionDetails.getType());
        transaction.setCategoryId(transactionDetails.getCategoryId());
        transaction.setDescription(transactionDetails.getDescription());
        transaction.setPaymentMethod(transactionDetails.getPaymentMethod());
        transaction.setReferenceId(transactionDetails.getReferenceId());
        transaction.setUserId(transactionDetails.getUserId());
        transaction.setEventId(transactionDetails.getEventId());
        transaction.setActivityId(transactionDetails.getActivityId());
        transaction.setMembershipId(transactionDetails.getMembershipId());
        transaction.setInventoryItemId(transactionDetails.getInventoryItemId());
        if (transactionDetails.getProjectId() != null) transaction.setProjectId(transactionDetails.getProjectId());
        validateProject(transaction);

        // Update new fields
        transaction.setVerified(transactionDetails.isVerified());
        transaction.setVerifiedBy(transactionDetails.getVerifiedBy());
        transaction.setVerifiedAt(transactionDetails.getVerifiedAt());
        transaction.setRenewal(transactionDetails.isRenewal());
        transaction.setRenewalYear(transactionDetails.getRenewalYear());
        transaction.setQuotaPeriod(transactionDetails.getQuotaPeriod());

        transaction.setUpdatedAt(LocalDateTime.now());

        calculateCommissions(transaction);
        // Note: We typically don't re-process renewal on update to avoid double extension,
        // unless explicitly handled. For now, we skip it on update.

        Transaction updatedTransaction = transactionRepository.save(transaction);

        // Update Journal Entry (Delete old and recreate new for simplicity)
        List<JournalEntry> existingEntries = journalEntryRepository.findByTransactionId(id);
        journalEntryRepository.deleteAll(existingEntries);
        createJournalEntryFromTransaction(updatedTransaction);

        return updatedTransaction;
    }

    private void validateProject(Transaction transaction) {
        if (transaction.getProjectId() == null) return;
        if (projects == null) throw new IllegalStateException("Project validation unavailable");
        projects.lockForFinancialWrite(transaction.getProjectId());
        var project = projects.findById(transaction.getProjectId())
                .orElseThrow(() -> new SuiteException(404,"PROJECT_MISSING","Progetto del movimento non trovato."));
        if (!java.util.Objects.equals(project.getAssociationId(), transaction.getAssociationId())) {
            throw new SuiteException(403,"ASSOCIATION_FORBIDDEN","Il movimento e il progetto devono appartenere alla stessa associazione.");
        }
    }

    private void calculateCommissions(Transaction transaction) {
        if (transaction.getPaymentMethod() != null && transaction.getAmount() != null) {
            Optional<PaymentMethod> methodOpt = paymentMethodRepository.findByNameAndAssociationId(
                transaction.getPaymentMethod(), transaction.getAssociationId()
            );

            if (methodOpt.isPresent()) {
                PaymentMethod method = methodOpt.get();
                if (method.isHasCommission()) {
                    BigDecimal fixed = method.getFixedCommission() != null ? method.getFixedCommission() : BigDecimal.ZERO;
                    BigDecimal percentage = method.getPercentageCommission() != null ? method.getPercentageCommission() : BigDecimal.ZERO;

                    BigDecimal commission = fixed.add(
                        transaction.getAmount().multiply(percentage).divide(BigDecimal.valueOf(100))
                    );

                    transaction.setCommissionAmount(commission);
                    // Net amount = Gross - Commission
                    transaction.setNetAmount(transaction.getAmount().subtract(commission));
                    return;
                }
            }
        }
        // Default: No commission
        transaction.setCommissionAmount(BigDecimal.ZERO);
        transaction.setNetAmount(transaction.getAmount());
    }

    private void processMembershipRenewal(Transaction transaction) {
        if (transaction.isRenewal() && transaction.getMembershipId() != null) {
            Optional<UserAssociation> membershipOpt = userAssociationRepository.findById(transaction.getMembershipId());
            if (membershipOpt.isPresent()) {
                UserAssociation membership = membershipOpt.get();

                LocalDate newExpiration;
                LocalDate baseDate = (membership.getExpirationDate() != null && membership.getExpirationDate().isAfter(LocalDate.now()))
                                     ? membership.getExpirationDate()
                                     : LocalDate.now();

                if ("ANNUAL".equalsIgnoreCase(transaction.getQuotaPeriod())) {
                    newExpiration = baseDate.plusYears(1);
                } else if ("SEMIANNUAL".equalsIgnoreCase(transaction.getQuotaPeriod())) {
                    newExpiration = baseDate.plusMonths(6);
                } else if ("MONTHLY".equalsIgnoreCase(transaction.getQuotaPeriod())) {
                    newExpiration = baseDate.plusMonths(1);
                } else {
                    newExpiration = baseDate.plusYears(1); // Default
                }

                membership.setExpirationDate(newExpiration);
                membership.setLastRenewalDate(transaction.getDate());
                membership.setStatus("ACTIVE");

                userAssociationRepository.save(membership);
            }
        }
    }

    @Transactional
    public void deleteTransaction(Long id) {
        Transaction current=transactionRepository.findById(id).orElseThrow(() -> new SuiteException(404,"TRANSACTION_MISSING","Movimento non trovato."));
        if (current.getProjectId()!=null) projects.lockForFinancialWrite(current.getProjectId());
        if (transactionRepository.lockIfProjectUnchanged(id,current.getProjectId())!=1)
            throw new SuiteException(409,"TRANSACTION_CHANGED","Il movimento è stato attribuito da un'altra richiesta. Ricaricare prima di eliminarlo.");
        // Also delete associated journal entries
        List<JournalEntry> entries = journalEntryRepository.findByTransactionId(id);
        journalEntryRepository.deleteAll(entries);
        transactionRepository.deleteById(id);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public List<Transaction> getTransactions(Specification<Transaction> spec) {
        return transactionRepository.findAll(spec);
    }

    public Optional<Transaction> getTransactionById(Long id) {
        return transactionRepository.findById(id);
    }

    public Map<String, BigDecimal> getYearOverYearComparison(int currentYear) {
        return getYearOverYearComparison(currentYear,null);
    }

    public Map<String, BigDecimal> getYearOverYearComparison(int currentYear,Long associationId) {
        LocalDate startCurrent = LocalDate.of(currentYear, 1, 1);
        LocalDate endCurrent = LocalDate.of(currentYear, 12, 31);

        LocalDate startPrevious = LocalDate.of(currentYear - 1, 1, 1);
        LocalDate endPrevious = LocalDate.of(currentYear - 1, 12, 31);

        BigDecimal incomeCurrent = transactionRepository.sumScoped(associationId,TransactionType.INCOME, startCurrent, endCurrent);
        BigDecimal expenseCurrent = transactionRepository.sumScoped(associationId,TransactionType.EXPENSE, startCurrent, endCurrent);

        BigDecimal incomePrevious = transactionRepository.sumScoped(associationId,TransactionType.INCOME, startPrevious, endPrevious);
        BigDecimal expensePrevious = transactionRepository.sumScoped(associationId,TransactionType.EXPENSE, startPrevious, endPrevious);

        Map<String, BigDecimal> comparison = new HashMap<>();
        comparison.put("incomeCurrent", incomeCurrent != null ? incomeCurrent : BigDecimal.ZERO);
        comparison.put("expenseCurrent", expenseCurrent != null ? expenseCurrent : BigDecimal.ZERO);
        comparison.put("incomePrevious", incomePrevious != null ? incomePrevious : BigDecimal.ZERO);
        comparison.put("expensePrevious", expensePrevious != null ? expensePrevious : BigDecimal.ZERO);

        return comparison;
    }

    public Map<String, BigDecimal> getCustomComparison(int year1, int year2) {
        return getCustomComparison(year1,year2,null);
    }

    public Map<String, BigDecimal> getCustomComparison(int year1,int year2,Long associationId) {
        LocalDate start1 = LocalDate.of(year1, 1, 1);
        LocalDate end1 = LocalDate.of(year1, 12, 31);

        LocalDate start2 = LocalDate.of(year2, 1, 1);
        LocalDate end2 = LocalDate.of(year2, 12, 31);

        BigDecimal income1 = transactionRepository.sumScoped(associationId,TransactionType.INCOME, start1, end1);
        BigDecimal expense1 = transactionRepository.sumScoped(associationId,TransactionType.EXPENSE, start1, end1);

        BigDecimal income2 = transactionRepository.sumScoped(associationId,TransactionType.INCOME, start2, end2);
        BigDecimal expense2 = transactionRepository.sumScoped(associationId,TransactionType.EXPENSE, start2, end2);

        Map<String, BigDecimal> comparison = new HashMap<>();
        comparison.put("incomeYear1", income1 != null ? income1 : BigDecimal.ZERO);
        comparison.put("expenseYear1", expense1 != null ? expense1 : BigDecimal.ZERO);
        comparison.put("incomeYear2", income2 != null ? income2 : BigDecimal.ZERO);
        comparison.put("expenseYear2", expense2 != null ? expense2 : BigDecimal.ZERO);

        return comparison;
    }

    public List<JournalEntry> getJournalEntries(Long associationId, LocalDate startDate, LocalDate endDate) {
        return journalEntryRepository.findScoped(associationId,startDate,endDate);
    }

    public List<Transaction> getTransactionsForYear(Long associationId,int year) {
        LocalDate start=LocalDate.of(year,1,1),end=LocalDate.of(year,12,31);
        return getTransactions((root,query,cb) -> associationId==null ? cb.between(root.get("date"),start,end)
                : cb.and(cb.equal(root.get("associationId"),associationId),cb.between(root.get("date"),start,end)));
    }

    // --- Double Entry Logic ---

    private void createJournalEntryFromTransaction(Transaction transaction) {
        // This is a simplified logic to map simple transactions to double entry.
        // In a real system, this mapping would be configurable.

        JournalEntry entry = new JournalEntry();
        entry.setAssociationId(transaction.getAssociationId());
        entry.setDate(transaction.getDate());
        entry.setDescription(transaction.getDescription());
        entry.setTransactionId(transaction.getId());

        // Identify Accounts (Simplified: finding by code or creating placeholders)
        // We assume some default accounts exist: "1000" (Bank/Cash), "4000" (Revenue), "6000" (Expense)

        Account bankAccount = findOrCreateAccount(transaction.getAssociationId(), "1000", "Cassa/Banca", AccountType.ASSET);

        if (transaction.getType() == TransactionType.INCOME) {
            Account revenueAccount = findOrCreateAccount(transaction.getAssociationId(), "4000", "Ricavi Generici", AccountType.REVENUE);

            // Debit Bank (Asset increases), Credit Revenue
            JournalEntryLine debitLine = new JournalEntryLine();
            debitLine.setAccount(bankAccount);
            debitLine.setDebit(transaction.getAmount());
            entry.addLine(debitLine);

            JournalEntryLine creditLine = new JournalEntryLine();
            creditLine.setAccount(revenueAccount);
            creditLine.setCredit(transaction.getAmount());
            entry.addLine(creditLine);

        } else if (transaction.getType() == TransactionType.EXPENSE) {
            Account expenseAccount = findOrCreateAccount(transaction.getAssociationId(), "6000", "Spese Generiche", AccountType.EXPENSE);

            // Debit Expense, Credit Bank (Asset decreases)
            JournalEntryLine debitLine = new JournalEntryLine();
            debitLine.setAccount(expenseAccount);
            debitLine.setDebit(transaction.getAmount());
            entry.addLine(debitLine);

            JournalEntryLine creditLine = new JournalEntryLine();
            creditLine.setAccount(bankAccount);
            creditLine.setCredit(transaction.getAmount());
            entry.addLine(creditLine);
        }

        journalEntryRepository.save(entry);
    }

    private Account findOrCreateAccount(Long associationId, String code, String defaultName, AccountType type) {
        return accountRepository.findByAssociationIdAndCode(associationId, code)
                .orElseGet(() -> {
                    Account newAccount = new Account();
                    newAccount.setAssociationId(associationId);
                    newAccount.setCode(code);
                    newAccount.setName(defaultName);
                    newAccount.setType(type);
                    return accountRepository.save(newAccount);
                });
    }
}
