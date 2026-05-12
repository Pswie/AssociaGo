package com.associago.security;

import com.associago.association.Association;
import com.associago.association.AssociationService;
import com.associago.association.repository.AssociationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRecoveryEndToEndTests {

    static {
        if (System.getProperty("associago.data.path") == null) {
            File tmp = new File(
                    System.getProperty("java.io.tmpdir"),
                    "associago-test-" + UUID.randomUUID()
            );
            // noinspection ResultOfMethodCallIgnored
            tmp.mkdirs();
            System.setProperty("associago.data.path", tmp.getAbsolutePath());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssociationService associationService;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanAssociations() {
        associationRepository.deleteAll();
    }

    @Test
    void newBcryptDatabaseCanLogin() throws Exception {
        Association association = newAssociation("new-db", "New DB", "12345678901");
        Association saved = associationService.create(association);

        mockMvc.perform(postJson("/api/auth/login", Map.of(
                        "associationId", saved.getId(),
                        "password", "secret-password"
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isString());
    }

    @Test
    void legacyPlainTextDatabaseCanLoginAndIsUpgraded() throws Exception {
        Association saved = associationRepository.save(newAssociation("plain-db", "Plain DB", "12345678901"));

        mockMvc.perform(postJson("/api/auth/login", Map.of(
                        "associationId", saved.getId(),
                        "password", "secret-password"
                )))
                .andExpect(status().isOk());

        Association upgraded = associationRepository.findById(saved.getId()).orElseThrow();
        assertThat(upgraded.getPassword()).isNotEqualTo("secret-password");
        assertThat(passwordEncoder.matches("secret-password", upgraded.getPassword())).isTrue();
    }

    @Test
    void doubleHashedDatabaseCanResetPasswordWithFiscalCodeAndThenLogin() throws Exception {
        Association association = newAssociation("double-db", "Double DB", "12345678901");
        String firstHash = passwordEncoder.encode("secret-password");
        association.setPassword(passwordEncoder.encode(firstHash));
        Association saved = associationRepository.save(association);

        mockMvc.perform(postJson("/api/auth/login", Map.of(
                        "associationId", saved.getId(),
                        "password", "secret-password"
                )))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/recovery/associations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Double DB"))
                .andExpect(content().string(not(containsString("12345678901"))));

        mockMvc.perform(postJson("/api/auth/recovery/reset-password", Map.of(
                        "associationId", saved.getId(),
                        "fiscalCode", "12345678901",
                        "newPassword", "new-secret"
                )))
                .andExpect(status().isOk());

        mockMvc.perform(postJson("/api/auth/login", Map.of(
                        "associationId", saved.getId(),
                        "password", "new-secret"
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isString());
    }

    @Test
    void wrongFiscalCodeDoesNotResetPassword() throws Exception {
        Association saved = associationService.create(newAssociation("wrong-code", "Wrong Code", "12345678901"));

        mockMvc.perform(postJson("/api/auth/recovery/reset-password", Map.of(
                        "associationId", saved.getId(),
                        "fiscalCode", "00000000000",
                        "newPassword", "new-secret"
                )))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(postJson("/api/auth/login", Map.of(
                        "associationId", saved.getId(),
                        "password", "new-secret"
                )))
                .andExpect(status().isUnauthorized());
    }

    private Association newAssociation(String slug, String name, String fiscalCode) {
        Association association = new Association();
        association.setName(name);
        association.setSlug(slug);
        association.setEmail(slug + "@example.org");
        association.setPassword("secret-password");
        association.setType("APS");
        association.setTaxCode(fiscalCode);
        return association;
    }

    private org.springframework.test.web.servlet.RequestBuilder postJson(String url, Object body) throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}
