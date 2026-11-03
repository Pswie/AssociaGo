package com.associago.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class SuitePermissionCatalog {
    private final JsonNode source;
    private final Set<String> permissions;
    public SuitePermissionCatalog(ObjectMapper mapper) {
        try (var input = new ClassPathResource("suite-access-catalog.json").getInputStream()) {
            source = mapper.readTree(input);
        } catch (IOException error) { throw new IllegalStateException("Catalogo dei permessi non disponibile", error); }
        var all = new TreeSet<String>();
        source.path("applications").fields().forEachRemaining(app ->
                app.getValue().path("modules").fieldNames().forEachRemaining(module -> {
                    all.add(app.getKey() + "." + module + ".read");
                    all.add(app.getKey() + "." + module + ".write");
                }));
        permissions = Collections.unmodifiableSet(all);
    }
    public Set<String> all() { return permissions; }
    public Set<String> preset(String app, String role) {
        if (!source.path("applications").has(app)) throw invalid("Applicazione non valida.");
        if ("none".equals(role) || "custom".equals(role)) return Set.of();
        if (!Set.of("viewer", "editor", "manager").contains(role)) throw invalid("Ruolo non valido.");
        var selected = new TreeSet<String>();
        for (String permission : permissions) {
            if (!permission.startsWith(app + ".")) continue;
            if (!role.equals("manager") && permission.startsWith(app + ".settings.")) continue;
            if (!role.equals("viewer") || permission.endsWith(".read")) selected.add(permission);
        }
        return Collections.unmodifiableSet(selected);
    }
    public Map<String, Object> catalog(boolean directoryConfigured) {
        var presets = new LinkedHashMap<String, Object>();
        source.path("applications").fieldNames().forEachRemaining(app -> {
            var roles = new LinkedHashMap<String, Object>();
            for (String role : List.of("none", "viewer", "editor", "manager")) roles.put(role, preset(app, role));
            presets.put(app, roles);
        });
        var response = new LinkedHashMap<String, Object>();
        response.put("schemaVersion", 1); response.put("roles", List.of("none", "viewer", "editor", "manager", "custom"));
        response.put("applications", source.path("applications")); response.put("presets", presets);
        response.put("directoryConfigured", directoryConfigured);
        return response;
    }
    public Map<String, Object> normalize(JsonNode applications) {
        if (applications == null || !applications.isObject()) throw invalid("Specificare gli accessi delle applicazioni.");
        applications.fieldNames().forEachRemaining(app -> {
            if (!source.path("applications").has(app)) throw invalid("Applicazione non valida.");
        });
        var result = new LinkedHashMap<String, Object>();
        source.path("applications").fieldNames().forEachRemaining(app -> {
            var entry = applications.path(app);
            if (!entry.isMissingNode() && !entry.isObject()) throw invalid("Accesso applicazione non valido.");
            entry.fieldNames().forEachRemaining(field -> {
                if (!Set.of("role", "permissions", "enabled").contains(field)) throw invalid("Campo di accesso non valido.");
            });
            String role = entry.path("role").asText("none");
            if (!Set.of("none", "viewer", "editor", "manager", "custom").contains(role)) throw invalid("Ruolo non valido.");
            var supplied = new TreeSet<String>();
            var raw = entry.path("permissions");
            if (!raw.isMissingNode() && (!raw.isArray() || raw.size() > permissions.size())) throw invalid("Permessi non validi.");
            for (var value : raw) {
                if (!value.isTextual() || !permissions.contains(value.asText()) || !value.asText().startsWith(app + "."))
                    throw invalid("Permesso non previsto dal catalogo.");
                supplied.add(value.asText());
            }
            for (String value : List.copyOf(supplied)) if (value.endsWith(".write")) supplied.add(value.substring(0, value.length() - 5) + "read");
            Set<String> selected = role.equals("custom") ? supplied : preset(app, role);
            if (!role.equals("custom") && !supplied.isEmpty() && !supplied.equals(selected)) throw invalid("Il ruolo e i permessi non corrispondono: selezionare Personalizzato.");
            result.put(app, Map.of("role", role, "permissions", List.copyOf(selected)));
        });
        return result;
    }
    private SuiteAccessException invalid(String message) { return new SuiteAccessException(400, "INVALID_ACCESS", message); }
}
