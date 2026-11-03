package com.associago.finance;

import com.associago.access.SuiteAssociationScope;
import com.associago.suite.SuiteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final FinancialAccess access;
    private final Environment environment;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this(paymentMethodService, null, null);
    }

    @Autowired
    public PaymentMethodController(PaymentMethodService paymentMethodService, FinancialAccess access,
                                   Environment environment) {
        this.paymentMethodService = paymentMethodService;
        this.access = access;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<List<PaymentMethod>> getAll(@RequestParam Long associationId, Authentication actor) {
        require(actor, associationId, false);
        return ResponseEntity.ok(paymentMethodService.getAllByAssociation(associationId).stream()
                .map(method -> visible(method, actor)).toList());
    }

    @GetMapping("/active")
    public ResponseEntity<List<PaymentMethod>> getActive(@RequestParam Long associationId, Authentication actor) {
        require(actor, associationId, false);
        return ResponseEntity.ok(paymentMethodService.getActiveByAssociation(associationId).stream()
                .map(method -> visible(method, actor)).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentMethod> getById(@PathVariable Long id, Authentication actor) {
        PaymentMethod method = paymentMethodService.getById(id);
        require(actor, method.getAssociationId(), false);
        return ResponseEntity.ok(visible(method, actor));
    }

    @PostMapping
    public ResponseEntity<PaymentMethod> create(@RequestBody PaymentMethod paymentMethod, Authentication actor) {
        require(actor, paymentMethod.getAssociationId(), true);
        if (central() && paymentMethod.getId() != null)
            throw new SuiteException(400, "PAYMENT_METHOD_ID_FORBIDDEN", "L'identificativo viene assegnato dal servizio.");
        if (central() && !owner(actor) && paymentMethod.getConfiguration() != null) {
            if (!paymentMethod.getConfiguration().isBlank()) configurationForbidden();
            paymentMethod.setConfiguration(null);
        }
        return ResponseEntity.ok(visible(paymentMethodService.create(paymentMethod), actor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentMethod> update(@PathVariable Long id, @RequestBody PaymentMethod paymentMethod,
                                               Authentication actor) {
        if (central()) {
            PaymentMethod existing = paymentMethodService.getById(id);
            require(actor, existing.getAssociationId(), true);
            if (paymentMethod.getAssociationId() != null &&
                    !Objects.equals(existing.getAssociationId(), paymentMethod.getAssociationId()))
                throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Associazione non autorizzata.");
            if (!owner(actor)) {
                if (paymentMethod.getConfiguration() != null) configurationForbidden();
                // The SQL statement excludes credentials instead of copying a potentially stale value.
                return ResponseEntity.ok(visible(paymentMethodService.updateWithoutConfiguration(
                        id, existing.getAssociationId(), paymentMethod), actor));
            }
        }
        return ResponseEntity.ok(visible(paymentMethodService.update(id, paymentMethod), actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication actor) {
        if (central()) {
            PaymentMethod existing = paymentMethodService.getById(id);
            require(actor, existing.getAssociationId(), true);
            if (!owner(actor) && existing.getConfiguration() != null && !existing.getConfiguration().isBlank())
                configurationForbidden();
        }
        paymentMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private boolean central() {
        return environment != null && SuiteAssociationScope.enabled(environment);
    }

    private void require(Authentication actor, Long association, boolean write) {
        if (!central()) return;
        access.readAssociation(actor, null, association);
        SuiteAssociationScope.require(environment, actor, association, "associago.finance." + (write ? "write" : "read"));
    }

    private boolean owner(Authentication actor) {
        return actor instanceof JwtAuthenticationToken jwt && actor.isAuthenticated()
                && jwt.getToken().getSubject().equals(environment.getProperty("SUITE_ACCESS_OWNER_SUBJECT", ""));
    }

    private PaymentMethod visible(PaymentMethod method, Authentication actor) {
        if (!central() || owner(actor)) return method;
        PaymentMethod copy = new PaymentMethod();
        BeanUtils.copyProperties(method, copy);
        copy.setConfiguration(null);
        return copy;
    }

    private void configurationForbidden() {
        throw new SuiteException(403, "PAYMENT_CONFIGURATION_FORBIDDEN", "La configurazione del gestore dei pagamenti e riservata al titolare.");
    }
}
