package cz.inovatika.altoEditor.infrastructure.kramerius;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

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

    public KrameriusClient getClient(String instanceId) {
        KrameriusProperties.KrameriusInstance instance = config.getKrameriusInstances().get(instanceId);

        if (instance == null) {
            throw new KrameriusInstanceNotConfiguredException(
                    "Kramerius instance with ID " + instanceId + " not found");
        }

        return new K7Client(config.getKrameriusInstances().get(instanceId),
                restTemplateBuilder.requestFactory(SimpleClientHttpRequestFactory.class).build(),
                krameriusUserFactory);
    }
}
