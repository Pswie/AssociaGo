package com.associago.suite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SuiteGateway {
    private final SuiteSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    public SuiteGateway(SuiteSettings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonNode request(String method, String suffix, Object body) {
        URI origin = settings.librepmApi();
        if (!suffix.matches("(?:/[a-zA-Z0-9-]+)*")) {
            throw new SuiteException(400, "INVALID_PATH", "Percorso del collegamento non valido.");
        }
        URI uri = URI.create(origin + "/api/integrations/v1/peer/projects" + suffix);
        int timeout = timeoutSeconds();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout))
                    .header("X-Suite-Peer", "associago").header("X-Suite-Key", settings.key())
                    .header("X-Suite-Organization", settings.organizationId()).header("Accept", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").method(method,
                        HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            }
            CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request.build(), ignored -> new LimitedBody(MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response;
            try {
                response = pending.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                pending.cancel(true);
                throw new SuiteException(503, "PEER_TIMEOUT", "Il registro non ha completato la risposta in tempo. Riprovare.");
            } catch (InterruptedException e) {
                pending.cancel(true);
                throw e;
            }
            {
                byte[] bytes = response.body();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    int status = response.statusCode() == 404 ? 404 : response.statusCode() == 409 ? 409 : 503;
                    throw new SuiteException(status, "REGISTRY_REJECTED", status == 404
                            ? "Progetto condiviso non trovato." : status == 409
                            ? "Il collegamento è già assegnato o la versione non è aggiornata."
                            : "Il registro LibrePM non è disponibile o rifiuta il collegamento. Verificare la configurazione server.");
                }
                JsonNode result = mapper.readTree(bytes);
                if (result == null || !(result.isObject() || result.isArray())) {
                    throw new SuiteException(503, "PEER_RESPONSE", "Risposta del registro non valida.");
                }
                if (result.isArray()) {
                    result.forEach(this::validateContext);
                } else {
                    validateContext(result);
                }
                return result;
            }
        } catch (SuiteException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuiteException(503, "PEER_UNAVAILABLE", "Collegamento al registro interrotto. Riprovare.");
        } catch (Exception e) {
            throw new SuiteException(503, "PEER_UNAVAILABLE", "Registro LibrePM non raggiungibile. I dati locali restano disponibili.");
        }
    }

    private int timeoutSeconds() {
        try {
            String configured = settings.value("SUITE_HTTP_TIMEOUT_SECONDS");
            int value = configured.isEmpty() ? 15 : Integer.parseInt(configured);
            if (value >= 1 && value <= 60) {
                return value;
            }
        } catch (NumberFormatException ignored) { }
        throw new SuiteException(503, "SUITE_CONFIGURATION", "Timeout del collegamento non valido (1–60 secondi).");
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private long received;
        private Flow.Subscription subscription;
        private boolean failed;

        private LimitedBody(long maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (failed) {
                return;
            }
            received += items.stream().mapToLong(ByteBuffer::remaining).sum();
            if (received > maximum) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IllegalStateException("Peer response limit exceeded"));
            } else {
                delegate.onNext(items);
            }
        }

        @Override
        public void onError(Throwable error) {
            if (!failed) {
                delegate.onError(error);
            }
        }

        @Override
        public void onComplete() {
            if (!failed) {
                delegate.onComplete();
            }
        }
    }

    private void validateContext(JsonNode context) {
        if (!context.isObject() || context.path("schemaVersion").asInt() != 1
                || !settings.organizationId().equals(context.path("organizationId").asText())) {
            throw new SuiteException(503, "PEER_RESPONSE", "Il registro ha restituito un contesto non valido.");
        }
        try {
            SuiteSettings.uuid(context.path("suiteProjectId").asText(), "Progetto condiviso");
        } catch (SuiteException invalidId) {
            throw new SuiteException(503, "PEER_RESPONSE", "Il registro ha restituito un identificativo non valido.");
        }
    }

    public JsonNode userView(JsonNode source, boolean financial) {
        JsonNode view = source.deepCopy();
        if (!financial) {
            if (view.isArray()) {
                view.forEach(this::redactFinancial);
            } else {
                redactFinancial(view);
            }
        }
        return view;
    }

    private void redactFinancial(JsonNode context) {
        if (context instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.putNull("financial");
            if (object.get("preparation") instanceof com.fasterxml.jackson.databind.node.ObjectNode preparation) {
                preparation.putNull("requestedGrant");
            }
        }
    }
}
