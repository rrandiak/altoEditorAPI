package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TuzkaWsClientTest {

    private static final String EXTERNAL_ID = "5a1b7e4c-0f3d-4a8e-9c2b-1d6e8f0a2b3c";

    private final TuzkaWsClient ws = new TuzkaWsClient("https://taas.example", "key", new ObjectMapper());

    @Test
    @DisplayName("a taas 0.9 done event completes the job registered under its external_id")
    void completesOnExternalId() throws Exception {
        CompletableFuture<TuzkaEvent> future = ws.register(EXTERNAL_ID);

        ws.onMessage("{\"job_id\":\"9fe44e86-f474-4cab-b5dd-317fa5ee064a\",\"external_id\":\""
                + EXTERNAL_ID + "\",\"status\":\"done\",\"alto_url\":\"https://r/a.xml.zst\"}");

        assertThat(future).isCompleted();
        TuzkaEvent event = future.get();
        assertThat(event.getStatus()).isEqualTo("done");
        assertThat(event.getJobId()).isEqualTo("9fe44e86-f474-4cab-b5dd-317fa5ee064a");
        assertThat(event.getAltoUrl()).isEqualTo("https://r/a.xml.zst");
    }

    @Test
    @DisplayName("a taas 0.8 event, which carries the external id as uuid, still matches")
    void completesOnLegacyUuid() {
        CompletableFuture<TuzkaEvent> future = ws.register(EXTERNAL_ID);

        ws.onMessage("{\"uuid\":\"" + EXTERNAL_ID + "\",\"status\":\"failed\",\"error\":\"boom\"}");

        assertThat(future).isCompleted();
    }

    @Test
    @DisplayName("an event for another job leaves the registered one waiting")
    void ignoresOtherJobs() {
        CompletableFuture<TuzkaEvent> future = ws.register(EXTERNAL_ID);

        ws.onMessage("{\"job_id\":\"x\",\"external_id\":\"someone-else\",\"status\":\"done\"}");

        assertThat(future).isNotDone();
    }
}
