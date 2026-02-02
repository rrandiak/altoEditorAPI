package cz.inovatika.altoEditor.presentation.facade;

import org.hibernate.search.engine.search.query.SearchResult;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.AltoVersionState;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.AltoVersion;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.AltoVersionService;
import cz.inovatika.altoEditor.domain.service.EngineService;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionUploadContent;
import cz.inovatika.altoEditor.domain.service.container.AltoVersionWithContent;
import cz.inovatika.altoEditor.exception.AltoVersionNotFoundException;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRelatedRequest;
import cz.inovatika.altoEditor.presentation.dto.request.AltoVersionSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionDto;
import cz.inovatika.altoEditor.presentation.dto.response.AltoVersionSearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.mapper.AltoVersionMapper;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AltoVersionFacade {

    private final AltoVersionService service;

    private final KrameriusService krameriusService;

    private final KrameriusProperties krameriusConfig;

    private final UserContextService userContext;

    private final AltoVersionMapper mapper;

    private final BatchRepository batchRepository;

    private final EngineService engineService;
    
    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory processFactory;

    private final BatchMapper batchMapper;

    public SearchResultsDto<AltoVersionSearchDto> searchRelated(AltoVersionSearchRelatedRequest request) {
        SearchResult<AltoVersion> results = service.searchRelated(
                userContext.getUserId(),
                request.getInstanceId(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                request.getOffset(),
                request.getLimit());
        
        return SearchResultsDto.<AltoVersionSearchDto>builder()
                .items(results.hits().stream().map(mapper::toSearchDto).toList())
                .total(results.total().hitCount())
                .build();
    }

    public SearchResultsDto<AltoVersionSearchDto> searchAll(AltoVersionSearchRequest request) {
        SearchResult<AltoVersion> results = service.search(
                request.getUsers(),
                request.getInstanceId(),
                request.getTargetPid(),
                request.getHierarchyPid(),
                request.getTitle(),
                request.getCreatedAfter(),
                request.getCreatedBefore(),
                request.getStates(),
                request.getOffset(),
                request.getLimit());
        
        return SearchResultsDto.<AltoVersionSearchDto>builder()
                .items(results.hits().stream().map(mapper::toSearchDto).toList())
                .total(results.total().hitCount())
                .build();
    }

    public AltoVersionDto getRelatedAlto(String pid, String instanceId) {
        AltoVersionWithContent digitalObjectWithContent = service.findRelatedAlto(pid,
                userContext.getUserId());

        if (digitalObjectWithContent == null) {
            digitalObjectWithContent = service.fetchNewAlto(pid, instanceId, userContext.getUserId(),
                    userContext.getToken());
        }

        return mapper.toDto(digitalObjectWithContent);
    }

    public AltoVersionDto getAltoVersion(String pid, Integer version) {
        AltoVersionWithContent digitalObjectWithContent = service.getAltoVersion(pid, version);

        return mapper.toDto(digitalObjectWithContent);
    }

    public AltoVersionDto getActiveAlto(String pid) {
        AltoVersionWithContent digitalObjectWithContent = service.getActiveAlto(pid);

        return mapper.toDto(digitalObjectWithContent);
    }

    public AltoVersionDto createNewAltoVersion(String pid, String altoContent) {
        AltoVersionWithContent digitalObjectWithContent = service.updateOrCreateAlto(pid, userContext.getUserId(),
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

    public BatchDto generateAlto(String pid, String engine, BatchPriority priority) {
        if (service.findRelated(pid, userContext.getUserId()) == null) {
            throw new AltoVersionNotFoundException(
                    "No digital object found for PID: " + pid + " and current user");
        }
        if (!engineService.isEngineEnabled(engine)) {
            throw new IllegalArgumentException("Engine '" + engine + "' is not enabled");
        }

        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.GENERATE_SINGLE)
                .pid(pid)
                .priority(priority)
                .engine(engine)
                .build());

        processDispatcher.submit(processFactory.create(batch, userContext.getCurrentUser()));

        return batchMapper.toDto(batch);
    }

    public void setActive(int objectId) {
        AltoVersionUploadContent content = service.getAltoVersionUploadContent(objectId);

        krameriusService.uploadAltoOcr(content.getPid(), content.getInstance(), content.getAltoContent(),
                content.getOcrContent(), userContext.getToken());

        service.setObjectActive(objectId);
    }

    public void reject(int objectId) {
        service.setStateForObject(objectId, AltoVersionState.REJECTED);
    }

    public void archive(int objectId) {
        service.setStateForObject(objectId, AltoVersionState.ARCHIVED);
    }
}
