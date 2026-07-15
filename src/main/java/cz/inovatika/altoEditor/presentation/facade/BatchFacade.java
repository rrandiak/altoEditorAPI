package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.service.UserService;
import cz.inovatika.altoEditor.presentation.dto.request.BatchSearchRequest;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import lombok.RequiredArgsConstructor;

/** Facade for batch job search and creation (list/filter batches, create reindex batch). */
@Component
@RequiredArgsConstructor
public class BatchFacade {

    private final BatchService service;

    private final BatchMapper mapper;

    private final UserService userService;

    /** Search batches with filters and Spring pagination. */
    public Page<BatchDto> searchBatches(
            BatchSearchRequest request,
            Pageable pageable) {

        Long createdById = null;
        if (request.getCreatedBy() != null && !request.getCreatedBy().isBlank()) {
            User user = userService.getUserByUsername(request.getCreatedBy());
            createdById = user.getId();
        }

        Page<Batch> page = service.search(request.getPid(), request.getState(), request.getSubstate(),
                request.getCreatedAfter(), request.getCreatedBefore(), request.getUpdatedAfter(),
                request.getUpdatedBefore(),
                request.getPriority(), request.getType(), request.getInstance(), createdById,
                request.getParentBatchId(), pageable);

        return page.map(mapper::toDto);
    }

    /** Fetch a single batch by id. */
    public BatchDto getBatch(Integer id) {
        return mapper.toDto(service.getById(id));
    }
}
