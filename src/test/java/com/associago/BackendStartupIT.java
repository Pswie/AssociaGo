package com.associago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("desktop")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendStartupIT {

    private static final Path DATA_PATH;

    static {
        File tmp = new File(
            System.getProperty("java.io.tmpdir"),
            "associago-startup-it-" + UUID.randomUUID()
        );
        // noinspection ResultOfMethodCallIgnored
        tmp.mkdirs();
        DATA_PATH = tmp.toPath();
        System.setProperty("associago.data.path", DATA_PATH.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writesIpv4ConnectionInfoAndHealthIsReachable() throws Exception {
        Path connectionFile = DATA_PATH.resolve("config").resolve("connection.json");
        JsonNode json = readConnectionJson(connectionFile);

        assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.path("protocol").asText()).isEqualTo("http");
        assertThat(json.path("host").asText()).isEqualTo("127.0.0.1");
        assertThat(json.path("port").asInt()).isEqualTo(port);
        assertThat(json.path("baseUrl").asText()).isEqualTo("http://127.0.0.1:" + port);
        assertThat(json.path("apiBaseUrl").asText()).isEqualTo("http://127.0.0.1:" + port + "/api");
        assertThat(json.path("healthUrl").asText()).isEqualTo("http://127.0.0.1:" + port + "/actuator/health");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(json.path("healthUrl").asText()))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private JsonNode readConnectionJson(Path connectionFile) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(connectionFile)) {
                return objectMapper.readTree(connectionFile.toFile());
            }
            Thread.sleep(100);
        }

        throw new AssertionError("connection.json was not written at " + connectionFile);
    }
}
