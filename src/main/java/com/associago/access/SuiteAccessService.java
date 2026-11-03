package com.associago.access;

import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SuiteAccessService {
    public static final String REQUEST_ATTRIBUTE = SuiteAccessService.class.getName() + ".view";
    public static final List<String> APPS = List.of("associago", "librepm", "bandi");
    private final SuiteAccessUserRepository users;
    private final SuiteAccessAuditRepository audit;
    private final UserRepository localUsers;
    private final SuitePermissionCatalog catalog;
    private final ClerkDirectoryClient directory;
    private final ObjectMapper mapper;
    private final Environment environment;
    public SuiteAccessService(SuiteAccessUserRepository users, SuiteAccessAuditRepository audit,
            UserRepository localUsers, SuitePermissionCatalog catalog, ClerkDirectoryClient directory,
            ObjectMapper mapper, Environment environment) {
        this.users = users; this.audit = audit; this.localUsers = localUsers; this.catalog = catalog;
        this.directory = directory; this.mapper = mapper; this.environment = environment;
    }
    public boolean enabled() { return environment.getProperty("SUITE_ACCESS_ENABLED", Boolean.class, false); }
    public String ownerSubject() {
        String value = environment.getProperty("SUITE_ACCESS_OWNER_SUBJECT", "").strip();
        if (!value.matches("user_[A-Za-z0-9]{8,120}")) throw new SuiteAccessException(503, "ACCESS_CONFIGURATION", "Gestione accessi non configurata.");
        return value;
    }
    public boolean owner(String subject) { return ownerSubject().equals(subject); }
    public String subject(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated())
            throw new SuiteAccessException(401, "AUTHENTICATION_REQUIRED", "Accedi per continuare.");
        String subject = jwt.getToken().getSubject(); validateSubject(subject); return subject;
    }
    public void requireOwner(Authentication authentication) {
        requireEnabled();
        if (!owner(subject(authentication))) throw new SuiteAccessException(403, "ACCESS_MANAGEMENT_FORBIDDEN", "Solo l'amministratore titolare gestisce gli accessi.");
    }
    public void requireEnabled() {
        if (!enabled()) throw new SuiteAccessException(503, "ACCESS_DISABLED", "Gestione accessi non disponibile.");
        ownerSubject();
    }
    public Map<String, Object> self(Authentication authentication) { return self(subject(authentication)); }
    public Map<String, Object> self(String subject) {
        requireEnabled(); validateSubject(subject);
        return view(ensureUser(subject));
    }
    private SuiteAccessUser ensureUser(String subject) {
        var existing = users.findById(subject);
        if (existing.isPresent()) return existing.get();
        var created = new SuiteAccessUser(subject);
        // Self registration grants nothing. Directory failures cannot grant access or bind by email.
        if (directory.configured()) {
            try { applyProfile(created, directory.user(subject)); }
            catch (SuiteAccessException unavailable) { /* pending identity remains usable without directory */ }
        }
        try { return users.saveAndFlush(created); }
        catch (DataIntegrityViolationException concurrentRegistration) {
            return users.findById(subject).orElseThrow(() -> concurrentRegistration);
        }
    }
    private void applyProfile(SuiteAccessUser user, ClerkDirectoryClient.Profile profile) {
        if (!user.getSubject().equals(profile.subject())) throw new SuiteAccessException(503, "DIRECTORY_IDENTITY_MISMATCH", "Identita Clerk non coerente.");
        user.profile(profile.displayName(), profile.firstName(), profile.lastName(), profile.email());
    }
    public Map<String, Object> catalog(Authentication authentication) {
        requireOwner(authentication); return catalog.catalog(directory.configured());
    }
    public Map<String, Object> list(Authentication authentication, int offset, int limit, String search) {
        requireOwner(authentication);
        if (offset < 0 || offset > 100000 || limit < 1 || limit > 100 || offset % limit != 0 || search.length() > 200)
            throw new SuiteAccessException(400, "INVALID_PAGE", "Pagina o ricerca non valida.");
        ensureUser(ownerSubject());
        var paging = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("subject")));
        var page = search.isBlank() ? users.findAll(paging) :
                users.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrSubjectContainingIgnoreCase(search, search, search, paging);
        return Map.of("items", page.getContent().stream().map(this::view).toList(), "total", page.getTotalElements(),
                "offset", offset, "limit", limit, "hasMore", page.hasNext());
    }
    @Transactional
    public Map<String, Object> sync(Authentication authentication, int offset) {
        requireOwner(authentication);
        var jwt = (JwtAuthenticationToken) authentication;
        var batch = directory.users(offset, jwt.getToken().getTokenValue());
        int created = 0;
        for (var profile : batch.profiles()) {
            var existing = users.findById(profile.subject());
            var user = existing.orElseGet(() -> new SuiteAccessUser(profile.subject()));
            if (existing.isEmpty()) created++;
            if (!Objects.equals(user.getDisplayName(), profile.displayName()) || !Objects.equals(user.getEmail(), profile.email()) ||
                    !Objects.equals(user.getFirstName(), profile.firstName()) || !Objects.equals(user.getLastName(), profile.lastName())) {
                applyProfile(user, profile); users.save(user);
            } else if (existing.isEmpty()) users.save(user);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("synced", batch.profiles().size()); result.put("created", created);
        result.put("complete", batch.complete()); result.put("nextOffset", batch.nextOffset()); return result;
    }
    @Transactional
    public Map<String, Object> update(Authentication authentication, String target, JsonNode input) {
        requireOwner(authentication); validateSubject(target);
        if (owner(target)) throw new SuiteAccessException(403, "OWNER_PROTECTED", "L'amministratore titolare e protetto e non puo essere sospeso o modificato.");
        if (!input.isObject() || input.toString().length() > 32768) throw invalid();
        input.fieldNames().forEachRemaining(field -> { if (!Set.of("version", "status", "applications").contains(field)) throw invalid(); });
        if (!input.path("version").isIntegralNumber() || !input.path("version").canConvertToLong() || input.path("version").asLong() < 0) throw invalid();
        String status = input.path("status").asText("");
        if (!Set.of("pending", "active", "suspended").contains(status)) throw invalid();
        var applications = catalog.normalize(input.get("applications"));
        var user = users.findById(target).orElseThrow(() -> new SuiteAccessException(404, "USER_NOT_FOUND", "Utente non trovato. Aggiorna l'elenco Clerk."));
        if (user.getVersion() != input.path("version").asLong()) throw new SuiteAccessException(409, "ACCESS_VERSION_CONFLICT", "Accessi modificati nel frattempo. Ricarica prima di salvare.");
        String before = json(Map.of("status", user.getStatus(), "applications", parseStored(user)));
        user.grant(status, json(applications));
        users.saveAndFlush(user);
        audit.save(new SuiteAccessAudit(subject(authentication), target, before, json(Map.of("status", status, "applications", applications))));
        return view(user);
    }
    public Map<String, Object> view(SuiteAccessUser user) {
        boolean owner = owner(user.getSubject());
        String status = owner ? "active" : user.getStatus();
        var configured = owner ? fullApplications() : parseStored(user);
        var applications = new LinkedHashMap<String, Object>();
        var flat = new TreeSet<String>();
        for (String app : APPS) {
            JsonNode node = mapper.valueToTree(configured.get(app));
            var effective = new ArrayList<String>();
            if ("active".equals(status)) for (var permission : node.path("permissions")) effective.add(permission.asText());
            flat.addAll(effective);
            applications.put(app, Map.of("enabled", !effective.isEmpty(), "role", node.path("role").asText("none"), "permissions", effective));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1); result.put("subject", user.getSubject()); result.put("displayName", user.getDisplayName());
        result.put("email", user.getEmail()); result.put("status", status); result.put("owner", owner); result.put("canManageAccess", owner);
        result.put("version", user.getVersion()); result.put("applications", applications); result.put("configuredApplications", configured);
        result.put("permissions", List.copyOf(flat)); return result;
    }
    private Map<String, Object> fullApplications() {
        var all = new LinkedHashMap<String, Object>();
        for (String app : APPS) all.put(app, Map.of("role", "manager", "permissions", List.copyOf(catalog.preset(app, "manager"))));
        return all;
    }
    private Map<String, Object> parseStored(SuiteAccessUser user) {
        try { return catalog.normalize(mapper.readTree(user.getApplicationsJson())); }
        catch (java.io.IOException | SuiteAccessException invalidStoredPolicy) {
            throw new SuiteAccessException(503, "ACCESS_POLICY_INVALID", "Configurazione accessi non valida. Nessun permesso concesso.");
        }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (java.io.IOException error) { throw new IllegalStateException("Access policy serialization failed"); }
    }
    /** A login account is not a legal association membership. Never create UserAssociation here. */
    public Optional<User> localIdentity(String subject) {
        if (!enabled()) return Optional.empty();
        var record = users.findById(subject);
        if (record.isEmpty()) return Optional.empty();
        var view = view(record.get());
        var app = mapper.valueToTree(view).path("applications").path("associago");
        if (!app.path("enabled").asBoolean(false)) return Optional.empty();
        var linked = localUsers.findByClerkUserId(subject);
        if (linked.isPresent()) return linked;
        var identity = new User(); identity.setClerkUserId(subject);
        identity.setFirstName(Optional.ofNullable(record.get().getFirstName()).filter(s -> !s.isBlank()).orElse(record.get().getDisplayName()));
        identity.setLastName(Optional.ofNullable(record.get().getLastName()).orElse("")); identity.setEmail(record.get().getEmail());
        try { return Optional.of(localUsers.saveAndFlush(identity)); }
        catch (DataIntegrityViolationException race) { return localUsers.findByClerkUserId(subject); }
    }
    public static boolean permits(Authentication authentication, String permission) {
        return authentication != null && authentication.isAuthenticated() && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SCOPE_" + permission));
    }
    public static void validateSubject(String value) {
        if (value == null || !value.matches("user_[A-Za-z0-9]{8,120}")) throw new SuiteAccessException(400, "INVALID_SUBJECT", "Identificativo Clerk non valido.");
    }
    private SuiteAccessException invalid() { return new SuiteAccessException(400, "INVALID_ACCESS", "Stato, versione o accessi non validi."); }
}
