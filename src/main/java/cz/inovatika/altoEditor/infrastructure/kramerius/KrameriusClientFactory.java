package cz.inovatika.altoEditor.infrastructure.kramerius;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.K7Client;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusClientFactory {

    private final KrameriusProperties config;

    private final RestTemplateBuilder restTemplateBuilder;

    public KrameriusClient getClient(String instanceId) {
        KrameriusProperties.KrameriusInstance instance = config.getKrameriusInstances().get(instanceId);

        if (instance == null) {
            // TODO: throw proper exception
            throw new IllegalArgumentException("Kramerius instance with ID " + instanceId + " not found");
        }

        return new K7Client(instance, restTemplateBuilder.build());
    }
}
