package cz.inovatika.altoEditor.api.facade;

import cz.inovatika.altoEditor.api.dto.BatchDto;
import cz.inovatika.altoEditor.api.dto.BatchSearchRequest;
import cz.inovatika.altoEditor.api.mapper.BatchMapper;
import cz.inovatika.altoEditor.core.entity.Batch;
import cz.inovatika.altoEditor.core.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchFacade {

    private final BatchService service;

    private final BatchMapper mapper;

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
