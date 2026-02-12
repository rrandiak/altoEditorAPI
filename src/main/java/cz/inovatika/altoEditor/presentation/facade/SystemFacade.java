package cz.inovatika.altoEditor.presentation.facade;

import org.springframework.stereotype.Component;

import cz.inovatika.altoEditor.domain.enums.BatchPriority;
import cz.inovatika.altoEditor.domain.service.BatchService;
import cz.inovatika.altoEditor.domain.model.Batch;
import cz.inovatika.altoEditor.infrastructure.process.ProcessDispatcher;
import cz.inovatika.altoEditor.infrastructure.process.reindex.ReindexProcessFactory;
import cz.inovatika.altoEditor.presentation.dto.response.BatchDto;
import cz.inovatika.altoEditor.presentation.mapper.BatchMapper;
import cz.inovatika.altoEditor.presentation.security.UserContextService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SystemFacade {
    
    private final BatchService batchService;

    private final ProcessDispatcher processDispatcher;

    private final ReindexProcessFactory reindexProcessFactory;

    private final UserContextService userContext;

    private final BatchMapper mapper;

    public BatchDto createReindexBatch(BatchPriority priority) {
        Long userId = userContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("No authenticated user");
        }
        Batch batch = batchService.createReindexBatch(priority, userContext.getUserId());
        processDispatcher.submit(reindexProcessFactory.create(batch));
        return mapper.toDto(batch);
    }
}
