package com.associago.finance;

import com.associago.suite.SuiteAccess;
import com.associago.suite.SuiteException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** The association is a server-checked scope, never a credential supplied by the browser. */
@Component
public class FinancialAccess {
    private final SuiteAccess access;
    private final Environment environment;
    public FinancialAccess(SuiteAccess access, Environment environment) {
        this.access = access;
        this.environment = environment;
    }

    public void requireRecord(Authentication actor, Long actualAssociation, Long selectedAssociation) {
        access.requireAssociation(actor, actualAssociation);
        if (!access.mayReadFinancial(actor)) {
            throw new SuiteException(403, "FINANCIAL_FORBIDDEN", "Il ruolo non consente di gestire i movimenti contabili.");
        }
        if (selectedAssociation != null && !Objects.equals(actualAssociation, selectedAssociation)) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Il movimento non appartiene all'associazione selezionata.");
        }
    }

    public Long readAssociation(Authentication actor, Long selectedAssociation, Long requestedAssociation) {
        if (selectedAssociation != null && requestedAssociation != null
                && !Objects.equals(selectedAssociation, requestedAssociation)) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Associazione della richiesta diversa da quella selezionata.");
        }
        Long association = selectedAssociation != null ? selectedAssociation : requestedAssociation;
        if (association == null && !environment.matchesProfiles("cloud")) {
            return null;
        }
        if (association == null) {
            throw new SuiteException(400, "ASSOCIATION_REQUIRED", "Selezionare l'associazione per consultare i dati finanziari.");
        }
        requireRecord(actor, association, null);
        return association;
    }
}
