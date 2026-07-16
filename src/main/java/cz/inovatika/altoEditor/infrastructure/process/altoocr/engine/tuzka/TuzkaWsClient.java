package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.Disposable;
import reactor.util.retry.Retry;

/**
 * One shared, auto-reconnecting taas WebSocket connection per engine. Callers
 * {@link #register(String)} a future for their external id before submitting a job; the
 * connection's terminal events ({@code done}/{@code failed}) complete the matching future
 * the instant they arrive — no polling, no completion lag.
 *
 * <p>On (re)connect taas replays terminal events finished within its catch-up window, so a
 * result produced during a brief disconnect is still delivered. Events for unknown ids
 * (already-completed, duplicated by replay) are ignored.
 */
public class TuzkaWsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TuzkaWsClient.class);

    private final URI uri;
    private final ReactorNettyWebSocketClient client;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CompletableFuture<TuzkaEvent>> pending = new ConcurrentHashMap<>();

    private volatile Disposable connection;

    public TuzkaWsClient(String baseUrl, String apiKey, ObjectMapper objectMapper) {
        this.uri = buildWsUri(baseUrl, apiKey);
        this.client = new ReactorNettyWebSocketClient();
        this.objectMapper = objectMapper;
    }

    /** Convert an http(s) base URL to the ws(s) /ws endpoint with the api key as a query param. */
    private static URI buildWsUri(String baseUrl, String apiKey) {
        String trimmed = baseUrl.replaceAll("/+$", "");
        String wsBase = trimmed.startsWith("https")
                ? "wss" + trimmed.substring("https".length())
                : trimmed.startsWith("http")
                        ? "ws" + trimmed.substring("http".length())
                        : trimmed;
        String query = apiKey == null ? "" : "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(wsBase + "/ws" + query);
    }

    /** Open the shared connection if not already open (idempotent). */
    public synchronized void start() {
        if (connection != null && !connection.isDisposed()) {
            return;
        }
        connection = client.execute(uri, session -> session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(this::onMessage)
                .then())
                .doOnError(e -> LOGGER.warn("taas WS connection error, will retry: {}", e.getMessage()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe();
    }

    /** Register a future for an external id (call before submitting the job). */
    public CompletableFuture<TuzkaEvent> register(String externalId) {
        CompletableFuture<TuzkaEvent> future = new CompletableFuture<>();
        pending.put(externalId, future);
        future.whenComplete((r, e) -> pending.remove(externalId, future));
        return future;
    }

    /** Drop a pending future (e.g. after a timeout) so the map does not leak. */
    public void unregister(String externalId) {
        pending.remove(externalId);
    }

    private void onMessage(String text) {
        try {
            TuzkaEvent event = objectMapper.readValue(text, TuzkaEvent.class);
            if (event.getUuid() == null) {
                return;
            }
            CompletableFuture<TuzkaEvent> future = pending.get(event.getUuid());
            if (future != null) {
                future.complete(event);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse taas WS message: {}", e.getMessage());
        }
    }

    /** Close the shared connection. */
    public void stop() {
        Disposable c = connection;
        if (c != null) {
            c.dispose();
        }
    }
}
