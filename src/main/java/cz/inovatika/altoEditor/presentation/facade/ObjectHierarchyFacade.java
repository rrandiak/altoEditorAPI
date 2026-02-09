package cz.inovatika.altoEditor.presentation.facade;

import java.util.List;

import org.hibernate.search.engine.search.query.SearchResult;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.infrastructure.process.retrieve.RetrieveHierarchyProcessFactory;
import cz.inovatika.altoEditor.presentation.dto.request.ObjectHierarchySearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.KrameriusDigitalObjectDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.mapper.KrameriusDigitalObjectMapper;
import cz.inovatika.altoEditor.presentation.mapper.ObjectHierarchyMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

/**
 * Facade for object hierarchy: search local index, fetch metadata/children from Kramerius,
 * and trigger batch jobs (fetch hierarchy, generate ALTO for subtree).
 */
@Component
@RequiredArgsConstructor
public class ObjectHierarchyFacade {

    private final ObjectHierarchyService service;

    private final ObjectHierarchyMapper mapper;

    private final KrameriusService krameriusService;

    private final KrameriusDigitalObjectMapper krameriusMapper;

    private final KrameriusProperties krameriusConfig;

    private final UserContextService userContext;

    private final BatchMapper batchMapper;

    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory altoGeneratorProcessFactory;

    private final RetrieveHierarchyProcessFactory retrieveHierarchyProcessFactory;

    /** Search hierarchy nodes (pagesCount/pagesWithAlto from persisted entity). */
    public SearchResultsDto<HierarchySearchDto> search(ObjectHierarchySearchRequest request) {
        SearchResult<DigitalObject> result = service.search(
                request.getPid(),
                request.getParentPid(),
                request.getModel(),
                request.getTitle(),
                request.getLevel(),
                request.getOffset(),
                request.getLimit());

        return SearchResultsDto.<HierarchySearchDto>builder()
                .items(result.hits().stream().map(mapper::toSearchDto).toList())
                .total(result.total().hitCount())
                .build();
    }

    /** Get object metadata from Kramerius (with children/pages counts). */
    public KrameriusDigitalObjectDto getObjectMetadata(String pid, String instance) {
        if (instance == null) {
            instance = krameriusConfig.getDefaultInstanceId();
        }

        return krameriusMapper.toDto(
                krameriusService.getObjectMetadata(pid, instance),
                krameriusService.getChildrenCount(pid, instance),
                krameriusService.getPagesCount(pid, instance));
    }

    /** Get children metadata from Kramerius for the given PID. */
    public List<KrameriusDigitalObjectDto> getChildrenMetadata(String pid, String instance) {
        String finalInstance = instance == null ? krameriusConfig.getDefaultInstanceId() : instance;

        return krameriusService.getChildrenMetadata(pid, finalInstance).stream()
                .map(metadata -> krameriusMapper.toDto(
                        metadata,
                        krameriusService.getChildrenCount(metadata.getPid(), finalInstance),
                        krameriusService.getPagesCount(metadata.getPid(), finalInstance)))
                .toList();
    }

    /** Start batch to generate ALTO for hierarchy rooted at PID. */
    public BatchDto generateAlto(String pid, BatchPriority priority) {
        Batch batch = service.createGenerateAltoBatch(pid, priority, userContext.getUserId());

        processDispatcher.submit(altoGeneratorProcessFactory.create(batch));

        return batchMapper.toDto(batch);
    }

    /** Start batch to fetch hierarchy from Kramerius and store locally. */
    public BatchDto fetchFromKramerius(String pid, BatchPriority priority) {
        Batch batch = service.createFetchFromKrameriusBatch(pid, priority, userContext.getUserId());

        processDispatcher.submit(retrieveHierarchyProcessFactory.create(batch));

        return batchMapper.toDto(batch);
    }

}
