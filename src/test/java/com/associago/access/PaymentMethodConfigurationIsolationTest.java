package com.associago.access;

import com.associago.finance.PaymentMethod;
import com.associago.finance.PaymentMethodService;
import com.associago.finance.repository.PaymentMethodRepository;
import com.associago.suite.SuiteException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JPQL on an isolated H2 database; no application files or external services are used. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@TestPropertySource(properties = {
    "SUITE_ACCESS_ENABLED=true", "SUITE_ACCESS_OWNER_SUBJECT=user_owner00000001", "SUITE_ASSOCIATION_ID=1",
    "associago.clerk.issuer-uri=https://synthetic.example.invalid", "associago.cors.allowed-origins=https://synthetic.example.invalid",
    "spring.datasource.url=jdbc:h2:mem:payment-method-scope-isolated;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class PaymentMethodConfigurationIsolationTest {
    @MockitoBean JwtDecoder decoder;
    @MockitoBean ClerkDirectoryClient directory;
    @Autowired PaymentMethodRepository repository;
    @Autowired PaymentMethodService service;
    @Autowired SuiteAccessUserRepository users;
    @Autowired SuiteAccessAuditRepository audit;
    @Autowired SuitePermissionCatalog catalog;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;

    @BeforeEach void clearOnlyThisInMemoryDatabase() {
        repository.deleteAll(); audit.deleteAll(); users.deleteAll();
    }

    private PaymentMethod method(Long association, String name, String configuration) {
        var method = new PaymentMethod();
        method.setAssociationId(association); method.setName(name); method.setType("BANK_TRANSFER");
        method.setConfiguration(configuration);
        return method;
    }

    @Test void staleOrdinaryEditCannotOverwriteAProviderUpdateOrEscapeAssociation() {
        var original = repository.saveAndFlush(method(1L, "Old label", "provider-v1"));
        var staleForm = method(1L, "Updated label", "provider-v1");
        // Owner commits new credentials after the ordinary editor loaded the old row.
        service.update(original.getId(), method(1L, "Old label", "provider-v2"));
        var updated = service.updateWithoutConfiguration(original.getId(), 1L, staleForm);
        assertEquals("Updated label", updated.getName());
        assertEquals("provider-v2", updated.getConfiguration());
        var foreign = repository.saveAndFlush(method(2L, "Other association", "foreign-provider"));
        assertEquals(409, assertThrows(SuiteException.class,
                () -> service.updateWithoutConfiguration(foreign.getId(), 1L, staleForm)).getStatus());
        var untouched = repository.findById(foreign.getId()).orElseThrow();
        assertEquals("Other association", untouched.getName());
        assertEquals("foreign-provider", untouched.getConfiguration());
    }

    @Test void editorRequestChangesOrdinaryFieldsButNeverReturnsOrReplacesProviderCredentials() throws Exception {
        String subject = "user_editor0000001";
        var user = new SuiteAccessUser(subject);
        user.grant("active", mapper.writeValueAsString(catalog.normalize(mapper.readTree(
                "{\"associago\":{\"role\":\"custom\",\"permissions\":[\"associago.finance.read\",\"associago.finance.write\"]}}"))));
        users.saveAndFlush(user);
        var original = repository.saveAndFlush(method(1L, "Old label", "provider-v2"));
        var body = mapper.writeValueAsString(Map.of("associationId", 1, "name", "Online edit", "type", "BANK_TRANSFER"));
        mvc.perform(put("/api/v1/payment-methods/" + original.getId()).with(jwt().jwt(token -> token.subject(subject)
                        .claim("role", "presidente").claim("o", Map.of("rol", "admin", "id", "org_unrelated"))))
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Online edit"))
                .andExpect(jsonPath("$.configuration").isEmpty());
        assertEquals("provider-v2", repository.findById(original.getId()).orElseThrow().getConfiguration());
        var malicious = mapper.writeValueAsString(Map.of("associationId", 1, "name", "Rejected", "type", "BANK_TRANSFER", "configuration", "replacement"));
        mvc.perform(put("/api/v1/payment-methods/" + original.getId()).with(jwt().jwt(token -> token.subject(subject)))
                .contentType("application/json").content(malicious))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PAYMENT_CONFIGURATION_FORBIDDEN"));
        assertEquals("provider-v2", repository.findById(original.getId()).orElseThrow().getConfiguration());
    }
}
