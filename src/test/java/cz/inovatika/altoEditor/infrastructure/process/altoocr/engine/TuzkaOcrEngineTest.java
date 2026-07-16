package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cz.inovatika.altoEditor.config.properties.EnginesProperties.EngineConfig;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaClient;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaEvent;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaWsClient;

@ExtendWith(MockitoExtension.class)
class TuzkaOcrEngineTest {

    @Mock
    private TuzkaClient client;
    @Mock
    private TuzkaWsClient wsClient;

    private static final String PAGE_UUID = "0a9c3ca3-8ee8-42f5-9857-fa8039beb9e1";
    private static final String PID = "uuid:" + PAGE_UUID;
    private static final byte[] IMAGE = new byte[] {1, 2, 3};

    private EngineConfig config(long timeout) {
        EngineConfig c = new EngineConfig();
        c.setTimeout(timeout);
        return c;
    }

    private TuzkaEvent event(String status, String error) {
        TuzkaEvent e = new TuzkaEvent();
        e.setStatus(status);
        e.setError(error);
        return e;
    }

    @Test
    @DisplayName("registers with the pid's uuid as external id, awaits done, downloads the ALTO")
    void happyPath() {
        when(wsClient.register(anyString())).thenReturn(CompletableFuture.completedFuture(event("done", null)));
        when(client.submit(anyString(), any())).thenReturn("job-1");
        when(client.downloadAlto("job-1")).thenReturn("<alto/>".getBytes());

        OcrResult result = new TuzkaOcrEngine(client, wsClient, config(10_000)).generate(PID, IMAGE);

        assertThat(new String(result.alto())).isEqualTo("<alto/>");
        assertThat(result.ocr()).isNull();

        // external id is the pid's uuid (used for both register and submit)
        ArgumentCaptor<String> externalId = ArgumentCaptor.forClass(String.class);
        verify(wsClient).register(externalId.capture());
        assertThat(externalId.getValue()).isEqualTo(PAGE_UUID);
        verify(client).submit(eq(PAGE_UUID), any());

        verify(wsClient).start();
        verify(wsClient).unregister(PAGE_UUID);
        verify(client).downloadAlto("job-1");
    }

    @Test
    @DisplayName("a failed event throws and never downloads")
    void failedEventThrows() {
        when(wsClient.register(anyString()))
                .thenReturn(CompletableFuture.completedFuture(event("failed", "boom")));
        when(client.submit(anyString(), any())).thenReturn("job-2");

        assertThatThrownBy(() -> new TuzkaOcrEngine(client, wsClient, config(10_000)).generate(PID, IMAGE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("boom");
        verify(client, never()).downloadAlto(anyString());
        verify(wsClient).unregister(anyString());
    }

    @Test
    @DisplayName("times out when no event arrives")
    void timesOut() {
        when(wsClient.register(anyString())).thenReturn(new CompletableFuture<>()); // never completes
        when(client.submit(anyString(), any())).thenReturn("job-3");

        assertThatThrownBy(() -> new TuzkaOcrEngine(client, wsClient, config(0)).generate(PID, IMAGE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Timed out");
        verify(client, never()).downloadAlto(anyString());
        verify(wsClient).unregister(anyString());
    }
}
