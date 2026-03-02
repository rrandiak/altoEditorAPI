package cz.inovatika.altoEditor.infrastructure.kramerius;

import java.time.Duration;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.exception.KrameriusInstanceNotConfiguredException;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.K7Client;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUserFactory;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClient;

@Component
@RequiredArgsConstructor
public class KrameriusClientFactory {

    private final KrameriusProperties config;
    private final KrameriusUserFactory krameriusUserFactory;

    private static final int MAX_IN_MEMORY_BYTES = 4 * 1024 * 1024; // 4MB

    public K7Client getClient(String instanceName) {

        KrameriusProperties.KrameriusInstance instance = 
                config.getKrameriusInstances().get(instanceName);

        if (instance == null) {
            throw new KrameriusInstanceNotConfiguredException(
                    "Kramerius instance with ID " + instanceName + " not found");
        }

        ConnectionProvider connectionProvider = ConnectionProvider.builder("kramerius-pool-" + instanceName)
                .maxConnections(instance.getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(instance.getPendingConnectionAcquireTimeout()))
                .maxIdleTime(Duration.ofMillis(instance.getMaxConnectionIdleTime()))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofMillis(instance.getReadTimeout()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) instance.getConnectTimeout())
                .compress(true);

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(instance.getUrl())
                .exchangeStrategies(exchangeStrategies)
                .build();

        return new K7Client(instance, webClient, krameriusUserFactory);
    }
}