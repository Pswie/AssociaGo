package com.associago.suite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuiteGatewayTests {
    private static final String ORG = "11111111-1111-4111-8111-111111111111";
    private static final String ID = "22222222-2222-4222-8222-222222222222";

    @ParameterizedTest
    @ValueSource(strings = {"http://example.org", "https://user:secret@example.org", "https://example.org?key=secret", "https://example.org#token", "https://example.org/api", "https://example.org:65536", "file:///tmp/backend", "http://127.0.0.1.evil.test"})
    void rejectsUnsafeConfiguredEndpoints(String value) {
        assertThatThrownBy(() -> SuiteSettings.safeOrigin(value)).isInstanceOf(SuiteException.class)
                .satisfies(error -> assertThat(((SuiteException) error).getStatus()).isEqualTo(503));
    }

    @Test
    void incompleteOrInvalidOrganizationFailsClosedAsConfigurationError() {
        MockEnvironment environment = environment().withProperty("SUITE_ORGANIZATION_ID", "not-an-uuid");
        assertThatThrownBy(() -> new SuiteSettings(environment).requireEnabled()).isInstanceOf(SuiteException.class)
                .satisfies(error -> assertThat(((SuiteException) error).getStatus()).isEqualTo(503));
        assertThatThrownBy(() -> new SuiteSettings(new MockEnvironment()).requireEnabled()).isInstanceOf(SuiteException.class)
                .hasMessageContaining("non configurato");
    }

    @Test
    void authenticatedTransportChecksOrganizationAndRedactsFinancialUserView() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger authenticated = new AtomicInteger();
        server.createContext("/api/integrations/v1/peer/projects/" + ID, exchange -> {
            if ("associago".equals(exchange.getRequestHeaders().getFirst("X-Suite-Peer"))
                    && ORG.equals(exchange.getRequestHeaders().getFirst("X-Suite-Organization"))) authenticated.incrementAndGet();
            byte[] body = ("{\"schemaVersion\":1,\"organizationId\":\"" + ORG + "\",\"suiteProjectId\":\"" + ID + "\",\"financial\":{\"income\":\"1.00\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            SuiteGateway gateway = gateway(server.getAddress().getPort(), "3");
            var result = gateway.request("GET", "/" + ID, null);
            assertThat(authenticated.get()).isEqualTo(1);
            assertThat(gateway.userView(result, false).path("financial").isNull()).isTrue();
            assertThat(result.path("financial").isObject()).isTrue();
        } finally { server.stop(0); }
    }

    @Test
    void deadlineIncludesBodyAfterHeadersRatherThanOnlyConnection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/api/integrations/v1/peer/projects", exchange -> {
            exchange.sendResponseHeaders(200, 2000);
            try (var output = exchange.getResponseBody()) {
                output.write('{'); output.flush();
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            } catch (java.io.IOException ignored) { }
        });
        server.start();
        try {
            long started = System.nanoTime();
            assertThatThrownBy(() -> gateway(server.getAddress().getPort(), "1").request("GET", "", null))
                    .isInstanceOf(SuiteException.class).satisfies(error -> assertThat(((SuiteException) error).getStatus()).isEqualTo(503));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        } finally { server.stop(0); executor.shutdownNow(); }
    }

    @Test
    void doesNotFollowRedirectsOrWriteToTheRedirectTarget() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger targetCalls = new AtomicInteger();
        server.createContext("/api/integrations/v1/peer/projects", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/target");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        server.createContext("/target", exchange -> { targetCalls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            assertThatThrownBy(() -> gateway(server.getAddress().getPort(), "2").request("POST", "", java.util.Map.of()))
                    .isInstanceOf(SuiteException.class);
            assertThat(targetCalls.get()).isZero();
        } finally { server.stop(0); }
    }

    @Test
    void redactsRequestedGrantInSingleAndListedContextsWithoutChangingSource() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var source = mapper.readTree("{\"financial\":{\"income\":\"1.00\"},\"preparation\":{\"requestedGrant\":{\"amount\":\"100.00\",\"currency\":\"EUR\"}}}");
        SuiteGateway gateway = new SuiteGateway(new SuiteSettings(environment()), mapper);
        assertThat(gateway.userView(source, false).path("preparation").path("requestedGrant").isNull()).isTrue();
        var list = mapper.createArrayNode().add(source);
        assertThat(gateway.userView(list, false).get(0).path("preparation").path("requestedGrant").isNull()).isTrue();
        assertThat(source.path("preparation").path("requestedGrant").isObject()).isTrue();
        assertThat(gateway.userView(source, true)).isEqualTo(source);
    }

    private SuiteGateway gateway(int port, String timeout) {
        return new SuiteGateway(new SuiteSettings(environment().withProperty("SUITE_LIBREPM_API_URL", "http://127.0.0.1:" + port)
                .withProperty("SUITE_HTTP_TIMEOUT_SECONDS", timeout)), new ObjectMapper());
    }

    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("SUITE_ENABLED", "true").withProperty("SUITE_ORGANIZATION_ID", ORG)
                .withProperty("SUITE_ASSOCIATION_ID", "1").withProperty("SUITE_ASSOCIAGO_KEY", "test-only-associago-peer-key-1234567890");
    }
}
