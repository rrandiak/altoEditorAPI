package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.inovatika.altoEditor.config.properties.EnginesProperties.EngineConfig;
import cz.inovatika.altoEditor.domain.adapter.PidAdapter;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaClient;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaEvent;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaJobState;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaWsClient;

/**
 * OCR engine backed by tuzka-as-a-service. Per page: register for the job's completion event
 * on the shared WebSocket, submit the image over HTTP, await the {@code done}/{@code failed}
 * event (bounded by the engine timeout), then download the ALTO. Using the WS event to signal
 * completion avoids polling lag and status-request overhead; concurrency across pages is still
 * handled by the caller's engine thread pool.
 */
public class TuzkaOcrEngine implements OcrEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(TuzkaOcrEngine.class);

    private final TuzkaClient client;
    private final TuzkaWsClient wsClient;
    private final long timeoutMillis;

    public TuzkaOcrEngine(TuzkaClient client, TuzkaWsClient wsClient, EngineConfig config) {
        this.client = client;
        this.wsClient = wsClient;
        this.timeoutMillis = config.getTimeout();
    }

    @Override
    public OcrResult generate(String pid, byte[] imageBytes) {
        wsClient.start();
        // Use the pid's UUID as the taas external id so jobs correlate to pages in taas.
        // (taas must allow re-submitting the same external id for re-OCR — a duplicate
        // rejection there is a taas-side issue, not something to work around by losing the
        // correlation.)
        String externalId = externalIdFor(pid);
        CompletableFuture<TuzkaEvent> future = wsClient.register(externalId);
        try {
            String jobId = client.submit(externalId, imageBytes);
            LOGGER.debug("taas job {} submitted for pid {} (external id {})", jobId, pid, externalId);

            TuzkaEvent event = awaitEvent(future, pid, externalId);
            if (TuzkaJobState.fromStatus(event.getStatus()) == TuzkaJobState.FAILED) {
                throw new RuntimeException("taas job failed for pid " + pid + ": " + event.getError());
            }

            return new OcrResult(client.downloadAlto(jobId), null);
        } finally {
            wsClient.unregister(externalId);
        }
    }

    /**
     * The taas external id (its {@code uuid} field) must parse as a UUID. Kramerius pids are
     * {@code uuid:<uuid>}, so use the pid's UUID part for correlation; fall back to a random
     * UUID if the pid is not a usable {@code uuid:} pid.
     */
    private String externalIdFor(String pid) {
        try {
            UUID uuid = PidAdapter.toUuid(pid);
            if (uuid != null) {
                return uuid.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // pid is not a "uuid:" pid — fall through to a random id
        }
        return UUID.randomUUID().toString();
    }

    private TuzkaEvent awaitEvent(CompletableFuture<TuzkaEvent> future, String pid, String externalId) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                    "Timed out waiting for taas job (pid " + pid + ", external id " + externalId + ")");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for taas job " + externalId, e);
        }
    }
}
