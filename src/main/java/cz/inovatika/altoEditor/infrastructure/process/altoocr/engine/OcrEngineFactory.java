package cz.inovatika.altoEditor.infrastructure.process.altoocr.engine;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.inovatika.altoEditor.config.properties.EnginesProperties;
import cz.inovatika.altoEditor.config.properties.EnginesProperties.EngineConfig;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaClient;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.engine.tuzka.TuzkaWsClient;
import cz.inovatika.altoEditor.infrastructure.storage.WorkDirectoryService;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

/**
 * Resolves the {@link OcrEngine} for a configured engine by its {@code type}: a stateless
 * {@link ExternalProcessOcrEngine} for SUBPROCESS engines, or a {@link TuzkaOcrEngine} over a
 * cached {@link TuzkaClient} + shared {@link TuzkaWsClient} for TUZKA engines.
 */
@Component
@RequiredArgsConstructor
public class OcrEngineFactory implements DisposableBean {

    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024; // ALTO can be large

    private final EnginesProperties enginesProperties;
    private final WorkDirectoryService workDirectoryService;
    private final ObjectMapper objectMapper;

    /** Cached taas HTTP clients per engine name (each wraps a WebClient with baseUrl + api key). */
    private final ConcurrentHashMap<String, TuzkaClient> tuzkaClients = new ConcurrentHashMap<>();
    /** Cached shared taas WebSocket clients per engine name (one connection each). */
    private final ConcurrentHashMap<String, TuzkaWsClient> tuzkaWsClients = new ConcurrentHashMap<>();

    public OcrEngine getEngine(String engineName) {
        EngineConfig config = enginesProperties.getEngineConfig(engineName);
        return switch (config.getType()) {
            case SUBPROCESS -> new ExternalProcessOcrEngine(config, workDirectoryService);
            case TUZKA -> new TuzkaOcrEngine(
                    tuzkaClients.computeIfAbsent(engineName, n -> buildTuzkaClient(config)),
                    tuzkaWsClients.computeIfAbsent(engineName, n -> buildTuzkaWsClient(config)),
                    config);
        };
    }

    private TuzkaClient buildTuzkaClient(EngineConfig config) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(config.getReadTimeoutMillis()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis());

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(config.getBaseUrl())
                .exchangeStrategies(strategies);

        if (config.shouldUseApiKey()) {
            builder.defaultHeader("X-API-Key", config.getApiKey());
        }

        return new TuzkaClient(builder.build(), config.getFmt());
    }

    private TuzkaWsClient buildTuzkaWsClient(EngineConfig config) {
        return new TuzkaWsClient(config.getBaseUrl(), config.getApiKey(), objectMapper);
    }

    @Override
    public void destroy() {
        tuzkaWsClients.values().forEach(TuzkaWsClient::stop);
    }
}
