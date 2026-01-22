package cz.inovatika.altoEditor.api.facade;

import cz.inovatika.altoEditor.api.auth.UserContextService;
import cz.inovatika.altoEditor.core.enums.DigitalObjectState;
import cz.inovatika.altoEditor.core.service.DigitalObjectService;
import cz.inovatika.altoEditor.core.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.kramerius.KrameriusService;
import cz.inovatika.altoEditor.kramerius.domain.KrameriusObjectMetadataDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KrameriusObjectFacade {

    private final KrameriusService service;

    private final DigitalObjectService digitalObjectService;

    private final UserContextService userContext;

    public KrameriusObjectMetadataDto getKrameriusObject(String pid, String instanceId) {
        return service.getObjectMetadata(pid, instanceId, userContext.getToken());
    }

    // Keep here because Kramerius is target of the upload
    // and DigitalObjectService is source of the data
    public void uploadObjectToKramerius(int objectId) {
        DigitalObjectUploadContent content = digitalObjectService.getDigitalObjectUploadContent(objectId);

        service.uploadAltoOcr(content.getInstance(), content.getPid(), content.getAltoContent(),
                content.getOcrContent(), userContext.getToken());
        digitalObjectService.setStateForObject(objectId, DigitalObjectState.UPLOADED);
    }

}
