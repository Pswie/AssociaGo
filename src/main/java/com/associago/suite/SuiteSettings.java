package com.associago.suite;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class SuiteSettings {
    private final Environment environment;

    public SuiteSettings(Environment environment) {
        this.environment = environment;
    }

    public boolean enabled() {
        return Boolean.parseBoolean(value("SUITE_ENABLED"));
    }

    public String value(String key) {
        return environment.getProperty(key, "").trim();
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw new SuiteException(503, "SUITE_DISABLED", "Collegamento tra applicazioni non configurato.");
        }
        try {
            uuid(value("SUITE_ORGANIZATION_ID"), "Organizzazione della suite");
        } catch (SuiteException e) {
            throw new SuiteException(503, "SUITE_CONFIGURATION", "Configurare un UUID valido per l'organizzazione della suite.");
        }
        associationId();
        if (value("SUITE_ASSOCIAGO_KEY").length() < 32) {
            throw new SuiteException(503, "SUITE_CONFIGURATION", "Configurare la chiave server del collegamento (almeno 32 caratteri).");
        }
    }

    public String organizationId() {
        requireEnabled();
        return value("SUITE_ORGANIZATION_ID");
    }

    public String key() {
        requireEnabled();
        return value("SUITE_ASSOCIAGO_KEY");
    }

    public long associationId() {
        try {
            long id = Long.parseLong(value("SUITE_ASSOCIATION_ID"));
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) { }
        throw new SuiteException(503, "SUITE_CONFIGURATION", "Configurare l'associazione locale del collegamento.");
    }

    public URI librepmApi() {
        requireEnabled();
        return safeOrigin(value("SUITE_LIBREPM_API_URL"));
    }

    public static String uuid(String value, String label) {
        try {
            String parsed = UUID.fromString(value).toString();
            if (parsed.equals(value.toLowerCase(Locale.ROOT))) {
                return parsed;
            }
        } catch (RuntimeException ignored) { }
        throw new SuiteException(400, "INVALID_UUID", label + " non valido.");
    }

    public static URI safeOrigin(String raw) {
        try {
            URI uri = URI.create(raw);
            boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback))) {
                throw new IllegalArgumentException();
            }
            return URI.create(uri.toString().replaceAll("/+$", ""));
        } catch (RuntimeException ignored) {
            throw new SuiteException(503, "SUITE_CONFIGURATION", "Configurare un'origine backend HTTPS (HTTP ammesso solo su loopback).");
        }
    }
}
