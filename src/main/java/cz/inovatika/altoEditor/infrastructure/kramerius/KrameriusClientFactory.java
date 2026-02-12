package cz.inovatika.altoEditor.infrastructure.kramerius;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.exception.KrameriusInstanceNotConfiguredException;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.K7Client;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUserFactory;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusClientFactory {

    private final KrameriusProperties config;

    private final RestTemplateBuilder restTemplateBuilder;

    private final KrameriusUserFactory krameriusUserFactory;

    public KrameriusClient getClient(String instanceName) {
        KrameriusProperties.KrameriusInstance instance = config.getKrameriusInstances().get(instanceName);

        if (instance == null) {
            throw new KrameriusInstanceNotConfiguredException(
                    "Kramerius instance with ID " + instanceName + " not found");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(instance.getConnectTimeout()));
        requestFactory.setReadTimeout(Duration.ofMillis(instance.getReadTimeout()));

        RestTemplate restTemplate = restTemplateBuilder
                .requestFactory(() -> requestFactory)
                .build();

        return new K7Client(instance, restTemplate, krameriusUserFactory);
    }
}
