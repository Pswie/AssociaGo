package com.associago.cloud;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Validates test URLs without DNS resolution or opening a database connection. */
final class LocalJdbcTestTarget {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private static final Set<String> CONNECTION_OVERRIDES = Set.of(
            "pghost", "pgport", "host", "port", "servername", "service", "socketfactory", "sslfactory",
            "socksproxyhost", "socksproxyport", "proxyhost", "proxyport", "dnssrv");

    private LocalJdbcTestTarget() {}

    static String requireLoopback(String jdbcUrl, String engine) {
        String prefix = "jdbc:" + engine + "://";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw invalidTarget();
        }
        final URI target;
        try {
            target = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException invalid) {
            throw invalidTarget();
        }
        String host = target.getHost();
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (!LOOPBACK.contains(host == null ? "" : host)
                || target.getRawUserInfo() != null || target.getRawFragment() != null
                || target.getPath() == null || target.getPath().length() <= 1) {
            throw invalidTarget();
        }
        if (target.getRawQuery() != null) {
            for (String parameter : target.getRawQuery().split("&")) {
                String key = parameter.split("=", 2)[0];
                try {
                    key = URLDecoder.decode(key, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                } catch (IllegalArgumentException invalid) {
                    throw invalidTarget();
                }
                if (CONNECTION_OVERRIDES.contains(key)) {
                    throw invalidTarget();
                }
            }
        }
        return jdbcUrl;
    }

    private static IllegalArgumentException invalidTarget() {
        // Do not include the URL: JDBC query parameters may contain credentials.
        return new IllegalArgumentException("Database integration tests require a literal loopback JDBC target "
                + "(127.0.0.1 or ::1), a database name, and no connection-routing overrides.");
    }
}
