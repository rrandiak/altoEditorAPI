package cz.inovatika.altoEditor.kramerius;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.KrameriusConfig;
import cz.inovatika.altoEditor.kramerius.k7.K7Client;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusClientFactory {

    private final KrameriusConfig config;

    private final RestTemplateBuilder restTemplateBuilder;

    public KrameriusClient getDefaultClient() {
        String defaultInstanceId = config.getKrameriusInstances().keySet().iterator().next();
        return getClient(defaultInstanceId);
    }

    public KrameriusClient getClient(String instanceId) {
        KrameriusConfig.KrameriusInstance instance = config.getKrameriusInstances().get(instanceId);

        if (instance == null) {
            // TODO: throw proper exception
            // throw new DigitalObjectNotFoundException(
            //         instanceId,
            //         "Kramerius instance is not configured"
            // );
        }

        return new K7Client(instance, restTemplateBuilder.build());
    }
}
