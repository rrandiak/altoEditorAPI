package cz.inovatika.altoEditor.application.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.DigitalObjectService;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.presentation.dto.request.DigitalObjectSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.DigitalObjectDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.mapper.DigitalObjectMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DigitalObjectFacade {

    private final DigitalObjectService service;

    private final UserContextService userContext;

    private final DigitalObjectMapper mapper;

    private final BatchRepository batchRepository;

    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory processFactory;

    private final BatchMapper batchMapper;

    public Page<DigitalObjectDto> searchRelatedDigitalObjects(DigitalObjectSearchRequest request, Pageable pageable) {
        userContext.getUsername();
        return service.search(request.getPid(), request.getUsername(),
                pageable).map(mapper::toDto);
    }

    public Page<DigitalObjectDto> searchDigitalObjects(DigitalObjectSearchRequest request, Pageable pageable) {
        return service.search(request.getPid(), request.getUsername(),
                pageable).map(mapper::toDto);
    }

    public DigitalObjectDto getDigitalObjectAlto(String pid, String instanceId, Integer version) {
        DigitalObjectWithContent digitalObjectWithContent = service.findAlto(pid, version,
                userContext.getUserId());

        if (digitalObjectWithContent == null) {
            digitalObjectWithContent = service.fetchNewAlto(pid, instanceId, userContext.getUserId(),
                    userContext.getToken());
        }

        return mapper.toDto(digitalObjectWithContent);
    }

    public DigitalObjectDto getDigitalObjectOriginalAlto(String pid) {
        DigitalObjectWithContent digitalObjectWithContent = service.getOriginalAlto(pid);

        return mapper.toDto(digitalObjectWithContent);
    }

    public DigitalObjectDto createNewAltoVersion(String pid, String altoContent) {
        DigitalObjectWithContent digitalObjectWithContent = service.updateOrCreateAlto(pid, userContext.getUserId(),
                altoContent);

        return mapper.toDto(digitalObjectWithContent);
    }

    public String getDigitalObjectOcr(String pid, Integer version) {
        return service.getOcr(pid, version, userContext.getUserId());
    }

    public byte[] getKrameriusObjectImage(String pid, String instanceId) {
        return service.getKrameriusObjectImage(pid, instanceId, userContext.getToken());
    }

    public BatchDto generateAlto(String pid, BatchPriority priority) {
        Batch batch = batchRepository.save(Batch.builder()
                .pid(pid)
                .priority(priority)
                .build());

        processDispatcher.submit(processFactory.create(batch, userContext.getCurrentUser()));

        return batchMapper.toDto(batch);
    }

    public void acceptDigitalObject(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.ACCEPTED);
    }

    public void rejectDigitalObject(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.REJECTED);
    }

}
