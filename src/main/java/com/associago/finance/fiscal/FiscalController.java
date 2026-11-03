package com.associago.finance.fiscal;

import com.associago.access.SuiteAssociationScope;
import com.associago.finance.FinancialAccess;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.SuiteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fiscal")
public class FiscalController {

    private final FiscalService fiscalService;
    private final FinancialAccess access;
    private final ClerkIdentityService identities;
    private final Environment environment;

    public FiscalController(FiscalService fiscalService) {
        this(fiscalService, null, null, null);
    }

    @Autowired
    public FiscalController(FiscalService fiscalService, FinancialAccess access,
                            ClerkIdentityService identities, Environment environment) {
        this.fiscalService = fiscalService;
        this.access = access;
        this.identities = identities;
        this.environment = environment;
    }

    @PostMapping("/close-year")
    public ResponseEntity<FiscalYearClosure> closeFiscalYear(
            @RequestParam Long associationId,
            @RequestParam Integer year,
            @RequestParam(required = false) Long userId, Authentication actor) {
        require(actor, associationId, true);
        Long author = author(actor, userId);
        try {
            return ResponseEntity.ok(fiscalService.closeFiscalYear(associationId, year, author));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build(); // Or return a specific error message
        }
    }

    @GetMapping("/closure")
    public ResponseEntity<FiscalYearClosure> getClosure(
            @RequestParam Long associationId,
            @RequestParam Integer year, Authentication actor) {
        require(actor, associationId, false);
        return fiscalService.getFiscalYearClosure(associationId, year)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/invoice-check")
    public ResponseEntity<InvoiceCheck> runInvoiceCheck(
            @RequestParam Long associationId,
            @RequestParam Integer year,
            @RequestParam(required = false) Long userId, Authentication actor) {
        require(actor, associationId, true);
        return ResponseEntity.ok(fiscalService.performInvoiceCheck(associationId, year, author(actor, userId)));
    }

    @GetMapping("/invoice-checks")
    public List<InvoiceCheck> getInvoiceCheckHistory(@RequestParam Long associationId, Authentication actor) {
        require(actor, associationId, false);
        return fiscalService.getInvoiceCheckHistory(associationId);
    }

    private boolean central() { return environment != null && SuiteAssociationScope.enabled(environment); }

    private void require(Authentication actor, Long association, boolean write) {
        if (!central()) return;
        access.readAssociation(actor, null, association);
        SuiteAssociationScope.require(environment, actor, association, "associago.finance." + (write ? "write" : "read"));
    }

    private Long author(Authentication actor, Long supplied) {
        if (!central()) {
            if (supplied == null) throw new SuiteException(400, "USER_REQUIRED", "Indicare l'utente.");
            return supplied;
        }
        return identities.resolve(actor).map(com.associago.user.User::getId)
                .orElseThrow(() -> new SuiteException(403, "IDENTITY_NOT_LINKED", "Accesso non collegato all'anagrafica."));
    }
}
