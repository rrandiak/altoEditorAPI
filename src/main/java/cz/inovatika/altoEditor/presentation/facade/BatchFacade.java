package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.presentation.dto.request.BatchSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import lombok.RequiredArgsConstructor;

/** Facade for batch job search (list/filter batches). */
@Component
@RequiredArgsConstructor
public class BatchFacade {

    private final BatchService service;

    private final BatchMapper mapper;

    /** Search batches with filters and Spring pagination. */
    public Page<BatchDto> searchBatches(
            BatchSearchRequest request,
            Pageable pageable) {
        Page<Batch> page = service.search(request.getPid(), request.getState(), request.getSubstate(),
                request.getCreatedAfter(), request.getCreatedBefore(), request.getUpdatedAfter(),
                request.getUpdatedBefore(),
                request.getPriority(), request.getType(), request.getInstance(), pageable);

        return page.map(mapper::toDto);
    }
}
