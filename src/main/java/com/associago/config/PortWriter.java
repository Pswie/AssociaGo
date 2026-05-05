package com.associago.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PortWriter implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PortWriter.class);
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String PROTOCOL = "http";

    private final ObjectMapper objectMapper;

    @Value("${associago.data.path}")
    private String dataPath;

    @Value("${server.address:127.0.0.1}")
    private String configuredHost;

    public PortWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        if (!(context instanceof ServletWebServerApplicationContext webServerContext)) {
            logger.debug("Skipping connection config write: application context has no web server");
            return;
        }

        int port = webServerContext.getWebServer().getPort();
        logger.info("AssociaGo ready on {}:{}", normalizeHost(configuredHost), port);
        writeConnectionJson(port);
    }

    private void writeConnectionJson(int port) {
        Path path = Paths.get(dataPath, "config", "connection.json");
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            String host = normalizeHost(configuredHost);
            String baseUrl = PROTOCOL + "://" + host + ":" + port;

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("schemaVersion", 1);
            config.put("protocol", PROTOCOL);
            config.put("host", host);
            config.put("port", port);
            config.put("baseUrl", baseUrl);
            config.put("apiBaseUrl", baseUrl + "/api");
            config.put("healthUrl", baseUrl + "/actuator/health");
            config.put("pid", currentPid());
            config.put("startedAt", Instant.now().toString());

            Path tmp = path.resolveSibling("connection.json.tmp-" + UUID.randomUUID());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), config);
            moveAtomically(tmp, path);

            logger.info("Connection config written to {}", path);
        } catch (IOException e) {
            logger.error("Failed to write connection config to file: {}", path, e);
        }
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            return DEFAULT_HOST;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
                return address.getHostAddress();
            }
        } catch (UnknownHostException e) {
            logger.warn("Invalid server.address '{}', falling back to {}", host, DEFAULT_HOST);
            return DEFAULT_HOST;
        }

        return host;
    }

    private long currentPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (Throwable ignored) {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int at = runtimeName.indexOf('@');
            if (at > 0) {
                try {
                    return Long.parseLong(runtimeName.substring(0, at));
                } catch (NumberFormatException ignoredAgain) {
                    return -1;
                }
            }
            return -1;
        }
    }

    private void moveAtomically(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
