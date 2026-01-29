package cz.inovatika.altoEditor.application.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.DigitalObjectState;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.DigitalObjectService;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectUploadContent;
import cz.inovatika.altoEditor.domain.service.container.DigitalObjectWithContent;
import cz.inovatika.altoEditor.exception.DigitalObjectNotFoundException;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
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

    private final KrameriusService krameriusService;

    private final KrameriusProperties krameriusConfig;

    private final UserContextService userContext;

    private final DigitalObjectMapper mapper;

    private final BatchRepository batchRepository;

    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory processFactory;

    private final BatchMapper batchMapper;

    public Page<DigitalObjectDto> searchRelated(DigitalObjectSearchRequest request, Pageable pageable) {
        Integer currentUserId = userContext.getUserId();
        List<Integer> users = request.getUsers() != null ? new ArrayList<>(request.getUsers()) : new ArrayList<>();
        if (!users.contains(currentUserId)) {
            users.add(currentUserId);
        }

        return service.search(
                users,
                request.getInstanceId(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getLabel(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                pageable).map(mapper::toDto);
    }

    public Page<DigitalObjectDto> searchAll(DigitalObjectSearchRequest request, Pageable pageable) {
        return service.search(
                request.getUsers(),
                request.getInstanceId(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getLabel(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                pageable).map(mapper::toDto);
    }

    public DigitalObjectDto getRelatedAlto(String pid, String instanceId) {
        DigitalObjectWithContent digitalObjectWithContent = service.findRelatedAlto(pid,
                userContext.getUserId());

        if (digitalObjectWithContent == null) {
            digitalObjectWithContent = service.fetchNewAlto(pid, instanceId, userContext.getUserId(),
                    userContext.getToken());
        }

        return mapper.toDto(digitalObjectWithContent);
    }

    public DigitalObjectDto getAltoVersion(String pid, Integer version) {
        DigitalObjectWithContent digitalObjectWithContent = service.getAltoVersion(pid, version);

        return mapper.toDto(digitalObjectWithContent);
    }

    public DigitalObjectDto getActiveAlto(String pid) {
        DigitalObjectWithContent digitalObjectWithContent = service.getActiveAlto(pid);

        return mapper.toDto(digitalObjectWithContent);
    }

    public DigitalObjectDto createNewAltoVersion(String pid, String altoContent) {
        DigitalObjectWithContent digitalObjectWithContent = service.updateOrCreateAlto(pid, userContext.getUserId(),
                altoContent);

        return mapper.toDto(digitalObjectWithContent);
    }

    public String getOcr(Integer objectId) {
        return service.getOcr(objectId);
    }

    public byte[] getImage(String pid, String instanceId) {
        return service.getKrameriusObjectImage(
                pid,
                instanceId != null ? instanceId : krameriusConfig.getDefaultInstanceId(),
                userContext.getToken());
    }

    public BatchDto generateAlto(String pid, BatchPriority priority) {
        if (service.findRelated(pid, userContext.getUserId()) == null) {
            throw new DigitalObjectNotFoundException(
                    "No digital object found for PID: " + pid + " and current user");
        }

        Batch batch = batchRepository.save(Batch.builder()
                .pid(pid)
                .priority(priority)
                .build());

        processDispatcher.submit(processFactory.create(batch, userContext.getCurrentUser()));

        return batchMapper.toDto(batch);
    }

    public void setActive(int objectId) {
        DigitalObjectUploadContent content = service.getDigitalObjectUploadContent(objectId);

        krameriusService.uploadAltoOcr(content.getPid(), content.getInstance(), content.getAltoContent(),
                content.getOcrContent(), userContext.getToken());

        service.setObjectActive(objectId);
    }

    public void reject(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.REJECTED);
    }

    public void archive(int objectId) {
        service.setStateForObject(objectId, DigitalObjectState.ARCHIVED);
    }
}
