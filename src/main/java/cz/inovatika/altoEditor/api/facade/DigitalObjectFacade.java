package cz.inovatika.altoEditor.api.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.api.auth.UserContextService;
import cz.inovatika.altoEditor.api.dto.DigitalObjectAltoDto;
import cz.inovatika.altoEditor.api.dto.DigitalObjectDto;
import cz.inovatika.altoEditor.api.dto.DigitalObjectSearchRequest;
import cz.inovatika.altoEditor.api.mapper.DigitalObjectMapper;
import cz.inovatika.altoEditor.core.entity.DigitalObject;
import cz.inovatika.altoEditor.core.enums.BatchPriority;
import cz.inovatika.altoEditor.core.enums.DigitalObjectState;
import cz.inovatika.altoEditor.core.service.DigitalObjectService;
import cz.inovatika.altoEditor.core.service.container.DigitalObjectWithContent;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DigitalObjectFacade {

    private final DigitalObjectService service;

    private final UserContextService userContext;

    private final DigitalObjectMapper mapper;

    public Page<DigitalObjectDto> searchDigitalObjects(DigitalObjectSearchRequest request, Pageable pageable) {
        Page<DigitalObject> page = service.search(request.getPid(), request.getLogin(),
                pageable);

        return page.map(mapper::toDto);
    }

    public DigitalObjectAltoDto getDigitalObjectAlto(String pid, String instanceId, String version) {
        DigitalObjectWithContent digitalObjectWithContent = service.findAlto(pid, version,
                userContext.getUserId());

        if (digitalObjectWithContent == null) {
            digitalObjectWithContent = service.fetchNewAlto(pid, instanceId, userContext.getUserId(), userContext.getToken());
        }

        return mapper.toAltoDto(digitalObjectWithContent);
    }

    public DigitalObjectAltoDto getDigitalObjectOriginalAlto(String pid) {
        DigitalObjectWithContent digitalObjectWithContent = service.getOriginalAlto(pid);

        return mapper.toAltoDto(digitalObjectWithContent);
    }

    public String createNewAltoVersion(String pid, String altoContent) {
        DigitalObjectWithContent digitalObjectWithContent = service.updateOrCreateAlto(pid, userContext.getUserId(), altoContent);

        return digitalObjectWithContent.getContent();
    }

    public String getDigitalObjectOcr(String pid, String version) {
        return service.getOcr(pid, version, userContext.getUserId());
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId) {
        return service.getKrameriusObjectImage(pid, instanceId, userContext.getToken());
    }

    public String generateAlto(String pid, BatchPriority priority) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'generateAlto'");
    }

    public void acceptDigitalObject(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.ACCEPTED);
    }

    public void rejectDigitalObject(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.REJECTED);
    }

}
