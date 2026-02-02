package cz.inovatika.altoEditor.presentation.facade;

import org.hibernate.search.engine.search.query.SearchResult;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.enums.BatchType;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.DigitalObject;
import cz.inovatika.altoEditor.domain.repository.BatchRepository;
import cz.inovatika.altoEditor.domain.service.ObjectHierarchyService;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.altoocr.AltoOcrGeneratorProcessFactory;
import cz.inovatika.altoEditor.presentation.dto.request.ObjectHierarchySearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.dto.response.HierarchySearchDto;
import cz.inovatika.altoEditor.presentation.dto.response.SearchResultsDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.mapper.ObjectHierarchyMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ObjectHierarchyFacade {

    private final ObjectHierarchyService service;

    private final UserContextService userContext;

    private final ObjectHierarchyMapper mapper;

    private final BatchRepository batchRepository;

    private final BatchMapper batchMapper;

    private final ProcessDispatcher processDispatcher;

    private final AltoOcrGeneratorProcessFactory processFactory;

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

    public BatchDto generateAlto(String pid, BatchPriority priority) {
        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.GENERATE)
                .pid(pid)
                .priority(priority)
                .build());

        processDispatcher.submit(processFactory.create(batch, userContext.getCurrentUser()));

        return batchMapper.toDto(batch);
    }

    public BatchDto fetchFromKramerius(String pid, BatchPriority priority) {
        Batch batch = batchRepository.save(Batch.builder()
                .type(BatchType.RETRIEVE_HIERARCHY)
                .pid(pid)
                .priority(priority)
                .build());

        processDispatcher.submit(processFactory.create(batch, userContext.getCurrentUser()));

        return batchMapper.toDto(batch);
    }

}
