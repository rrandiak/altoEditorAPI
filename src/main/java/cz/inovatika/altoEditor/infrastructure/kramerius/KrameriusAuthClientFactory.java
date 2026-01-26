package cz.inovatika.altoEditor.infrastructure.kramerius;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.AuthProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.adapter.k7.K7AuthClient;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusUserFactory;
import lombok.RequiredArgsConstructor;

/**
 * Factory for creating KrameriusAuthClient instances.
 * Currently, it only creates K7AuthClient instances.
 * But it can be extended in the future to support other Kramerius versions.
 */
@Component
@RequiredArgsConstructor
public class KrameriusAuthClientFactory {

    private final AuthProperties config;

    private final RestTemplateBuilder restTemplateBuilder;

    private final KrameriusUserFactory krameriusUserFactory;

    public KrameriusAuthClient getClient() {
        return new K7AuthClient(config, restTemplateBuilder.build(), krameriusUserFactory);
    }
}
