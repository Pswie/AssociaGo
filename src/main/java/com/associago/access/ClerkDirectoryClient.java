package com.associago.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Server-only directory reads; the Clerk credential never enters a response or log. */
@Component
public class ClerkDirectoryClient {
    public record Profile(String subject, String displayName, String firstName, String lastName, String email) { }
    public record Batch(List<Profile> profiles, boolean complete, Integer nextOffset) { }
    private final Environment environment;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public ClerkDirectoryClient(Environment environment, ObjectMapper mapper) {
        this.environment = environment; this.mapper = mapper;
    }
    public boolean configured() { return !environment.getProperty("CLERK_SECRET_KEY", "").isBlank()
            || !environment.getProperty("SUITE_CLERK_DIRECTORY_URL", "").isBlank(); }
    public Batch users(int offset, String ownerBearer) {
        if (offset < 0 || offset > 1_000_000) throw new SuiteAccessException(400, "INVALID_OFFSET", "Pagina non valida.");
        JsonNode result = environment.getProperty("CLERK_SECRET_KEY", "").isBlank()
                ? remoteUsers(offset, ownerBearer) : get("/users?limit=100&offset=" + offset + "&order_by=%2Bcreated_at");
        JsonNode data = result.isArray() ? result : result.path("data");
        if (!data.isArray() || data.size() > 100) throw unavailable();
        var profiles = new ArrayList<Profile>();
        for (var user : data) profiles.add(profile(user));
        boolean complete = data.size() < 100;
        if (result.path("total_count").canConvertToInt()) complete = offset + data.size() >= result.path("total_count").asInt();
        return new Batch(List.copyOf(profiles), complete, complete ? null : offset + data.size());
    }
    public Profile user(String subject) {
        SuiteAccessService.validateSubject(subject);
        return profile(get("/users/" + subject));
    }
    private JsonNode get(String path) {
        String secret = environment.getProperty("CLERK_SECRET_KEY", "").trim();
        if (!secret.startsWith("sk_test_") && !secret.startsWith("sk_live_")) throw unavailable();
        return authenticatedGet(URI.create("https://api.clerk.com/v1" + path), secret);
    }
    private JsonNode remoteUsers(int offset, String bearer) {
        if (bearer == null || bearer.length() > 16384 || bearer.isBlank()) throw unavailable();
        URI target;
        try {
            URI origin = URI.create(environment.getProperty("SUITE_CLERK_DIRECTORY_URL", ""));
            boolean local = List.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
            if (!("https".equals(origin.getScheme()) && "www.angelidelfuturoaps.com".equals(origin.getHost())
                    || local && "http".equals(origin.getScheme())) || origin.getUserInfo() != null || origin.getQuery() != null
                    || origin.getFragment() != null || !"/api/suite-directory/users".equals(origin.getPath())) throw unavailable();
            target = URI.create(origin + "?offset=" + offset + "&limit=100");
        } catch (IllegalArgumentException error) { throw unavailable(); }
        return authenticatedGet(target, bearer);
    }
    private JsonNode authenticatedGet(URI target, String bearer) {
        var request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json").header("User-Agent", "AngeliDelFuturo-Access/1.0").GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw unavailable();
                byte[] bytes = body.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw unavailable();
                return mapper.readTree(bytes);
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (java.io.IOException | IllegalArgumentException error) { throw unavailable(); }
    }
    private Profile profile(JsonNode user) {
        String subject = user.path("id").asText("");
        SuiteAccessService.validateSubject(subject);
        String first = bounded(user.path("first_name").asText(""), 100);
        String last = bounded(user.path("last_name").asText(""), 100);
        String display = (first + " " + last).trim();
        if (display.isBlank()) display = subject;
        String email = null;
        String primaryId = user.path("primary_email_address_id").asText("");
        for (var address : user.path("email_addresses")) {
            if (primaryId.equals(address.path("id").asText()) &&
                    "verified".equals(address.path("verification").path("status").asText())) {
                String candidate = address.path("email_address").asText("");
                if (!candidate.isBlank() && candidate.length() <= 254 && candidate.indexOf('@') > 0) email = candidate;
            }
        }
        return new Profile(subject, bounded(display, 200), first, last, email);
    }
    private String bounded(String value, int length) {
        String clean = value.replaceAll("[\\p{Cntrl}]", "").strip();
        return clean.length() <= length ? clean : clean.substring(0, length);
    }
    private SuiteAccessException unavailable() {
        return new SuiteAccessException(503, "CLERK_DIRECTORY_UNAVAILABLE", "Elenco Clerk non disponibile. Gli accessi salvati restano invariati.");
    }
}
