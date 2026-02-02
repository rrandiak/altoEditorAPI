package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusFacade {

    private final KrameriusService service;

    private final UserContextService userContext;

    private final KrameriusProperties krameriusConfig;

    public KrameriusObjectMetadata getObjectMetadata(String pid, String instanceId) {
        return service.getObjectMetadata(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());
    }
}
