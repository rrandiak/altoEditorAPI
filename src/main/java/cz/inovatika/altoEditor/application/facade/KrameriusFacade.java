package cz.inovatika.altoEditor.application.facade;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.service.DigitalObjectService;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.kramerius.model.KrameriusObjectMetadata;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KrameriusFacade {

    private final KrameriusService service;

    private final DigitalObjectService digitalObjectService;

    private final UserContextService userContext;

    private final KrameriusProperties krameriusConfig;

    public KrameriusObjectMetadata getKrameriusObject(String pid, String instanceId) {
        return service.getObjectMetadata(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());
    }

    // Keep here because Kramerius is target of the upload
    // and DigitalObjectService is source of the data
    public void uploadObjectToKramerius(int objectId) {
        DigitalObjectUploadContent content = digitalObjectService.getDigitalObjectUploadContent(objectId);

        service.uploadAltoOcr(content.getPid(), content.getInstance(), content.getAltoContent(),
                content.getOcrContent(), userContext.getToken());

        digitalObjectService.setStateForObject(objectId, DigitalObjectState.UPLOADED);
    }

}
