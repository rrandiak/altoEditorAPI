package cz.inovatika.altoEditor.presentation.facade;

import java.util.List;

import org.hibernate.search.engine.search.query.SearchResult;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.config.properties.KrameriusProperties;
import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.kramerius.KrameriusService;
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
                .items(result.hits().stream().map(mapper::toDto).toList())
                .total(result.total().hitCount())
                .build();
    }

    public KrameriusDigitalObjectDto getObjectMetadata(String pid, String instance) {
        if (instance == null) {
            instance = krameriusConfig.getDefaultInstanceId();
        }

        return krameriusMapper.toDto(
                krameriusService.getObjectMetadata(pid, instance),
                krameriusService.getChildrenCount(pid, instance),
                krameriusService.getPagesCount(pid, instance));
    }

    public List<KrameriusDigitalObjectDto> getChildrenMetadata(String pid, String instance) {
        String finalInstance = instance == null ? krameriusConfig.getDefaultInstanceId() : instance;

        return krameriusService.getChildrenMetadata(pid, finalInstance).stream()
                .map(metadata -> krameriusMapper.toDto(
                        metadata,
                        krameriusService.getChildrenCount(metadata.getPid(), finalInstance),
                        krameriusService.getPagesCount(metadata.getPid(), finalInstance)))
                .toList();
    }

    public BatchDto generateAlto(String pid, BatchPriority priority) {
        return batchMapper.toDto(service.generateAlto(pid, priority, userContext.getUserId()));
    }

    public BatchDto fetchFromKramerius(String pid, BatchPriority priority) {
        return batchMapper.toDto(service.fetchFromKramerius(pid, priority, userContext.getUserId()));
    }

}
