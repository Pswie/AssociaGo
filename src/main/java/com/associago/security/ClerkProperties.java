package com.associago.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurazione dell'identity provider Clerk per il profilo {@code cloud}.
 *
 * <p>Il backend non gestisce credenziali: riceve un JWT firmato da Clerk e ne
 * verifica la firma contro il JWKS pubblicato dall'issuer. Di conseguenza qui
 * non compare nessun segreto, solo indirizzi pubblici.
 *
 * @since 0.2.0
 */
@Component
@ConfigurationProperties(prefix = "associago.clerk")
public class ClerkProperties {

    /**
     * Issuer del token, cioè la Frontend API dell'istanza Clerk
     * (es. {@code https://clerk.angelidelfuturo.it} oppure
     * {@code https://<slug>.clerk.accounts.dev}). Spring risolve da qui
     * {@code /.well-known/openid-configuration} e quindi il JWKS.
     */
    private String issuerUri = "";

    /**
     * Audience attesa nel token. Vuota disattiva il controllo: accettabile solo
     * se l'istanza Clerk serve esclusivamente questa applicazione.
     */
    private String audience = "";

    /**
     * Claim da cui leggere il ruolo, in ordine di preferenza. Clerk non ha un
     * claim di ruolo standard: dipende da come è configurato il session token
     * template, per questo l'elenco è configurabile.
     */
    private List<String> roleClaims = new ArrayList<>(List.of("role", "roles", "org_role"));

    /**
     * Ruoli ammessi. Combaciano con quelli del sito (sito/src/data/admin.ts):
     * un ruolo nel token che non compare qui non concede autorizzazioni.
     */
    private List<String> knownRoles = new ArrayList<>(List.of(
            "presidente",
            "coordinatore-servizi",
            "psicologo",
            "operatore-alimentare",
            "orientatore-servizi",
            "facilitatore-digitale",
            "responsabile-amministrativo",
            "referente-volontari",
            // Ruoli senza accesso al gestionale: vedono solo la propria
            // posizione tramite /api/v1/me.
            "socio",
            "volontario"
    ));

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public List<String> getRoleClaims() {
        return roleClaims;
    }

    public void setRoleClaims(List<String> roleClaims) {
        this.roleClaims = roleClaims;
    }

    public List<String> getKnownRoles() {
        return knownRoles;
    }

    public void setKnownRoles(List<String> knownRoles) {
        this.knownRoles = knownRoles;
    }
}
